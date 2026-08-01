package com.hmdp.utils;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSON;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONString;
import cn.hutool.json.JSONUtil;
import com.hmdp.entity.Shop;
import io.lettuce.core.GeoArgs;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.utils.RedisConstants.CACHE_NULL_TTL;
import static com.hmdp.utils.RedisConstants.LOCK_SHOP_KEY;

@Component
@Slf4j
public class CacheClient {

    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    private final StringRedisTemplate stringRedisTemplate;

    public CacheClient(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    private boolean getLock(String lockKey) {
        Boolean flag= stringRedisTemplate.opsForValue().setIfAbsent(lockKey, "1", 10, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(flag);
    }

    private void unlock(String key) {
        stringRedisTemplate.delete(key);
    }

    /**
     * 将值存进redis
     * @param key
     * @param value
     * @param time
     * @param unit
     */
    public void set(String key, Object value, Long time, TimeUnit unit) {
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, unit);
    }
    public <ID, T> T queryWithPassThrough(ID id, Class<T> Type, Function<ID, T> dbFallback, String PrexKey) {
        String Key = PrexKey + id;
        //    先查redis
        String json = stringRedisTemplate.opsForValue().get(Key);
        log.info("redis里面json：{}",json);
        //如果不为空则直接返回
        if (StrUtil.isNotBlank(json)) {
            return JSONUtil.toBean(json, Type);
        }
        //不为空说明数据库没查到但是设置了空字符
        if (json != null) {
            return null;
        }
        //为null则查数据库然后redis设置
        T res = dbFallback.apply(id);
        log.info("数据库数据:{}",res);
        if (res == null) {
            stringRedisTemplate.opsForValue().set(Key, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }
        String str=JSONUtil.toJsonStr(res);
        stringRedisTemplate.opsForValue().set(Key,str, RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);

        return res;
    }

    public <R, ID> R queryWithMutex(
            String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback, Long time, TimeUnit unit){
        String key=keyPrefix+id;
        // 1.从redis查询商铺缓存
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        log.info("redis:{}",shopJson);
        // 2.判断是否存在
        if (StrUtil.isNotBlank(shopJson)) {
            // 3.存在，直接返回
            return JSONUtil.toBean(shopJson, type);
        }
        // 判断命中的是否是空值
        if (shopJson != null) {
            // 返回一个错误信息
            return null;
        }
        // 4.实现缓存重建
        // 4.1.获取互斥锁
        String lockKey = LOCK_SHOP_KEY + id;
        R r=null;
            try {
                boolean getLock=getLock(lockKey);
                if (!getLock) {
                    //如果没有获取到悲观锁 就让线程睡眠50ms再重试
                    Thread.sleep(50);
                    return queryWithMutex(keyPrefix, id, type, dbFallback, time, unit);
                }
                // 4.4.获取锁成功，根据id查询数据库
                r = dbFallback.apply(id);
                log.info("database:{}",r);
                // 5.不存在，返回错误
                if (r == null) {
                    // 将空值写入redis
                    stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.HOURS);
                    // 返回错误信息
                    return null;
                }
                // 6.存在，写入redis
                this.set(key, r, time, unit);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            } finally {
                unlock(lockKey);
            }
            return r;
    }



    public <R, ID> R queryWithLogicalExpire(
            String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback, Long time, TimeUnit unit) {
        /**
         * TODO
         * 有bug 用户第一次查得时候数据库里面没有数据 此时查询为异步如果直接查数据库就会穿透数据库
         * 不查则为空
         * 只能在第一次查数据库得时候弄个互斥锁去限制查询
         * 如果第一次为空就直接通过 querywithmutex方法 通过互斥锁去控制吧
         * 但是两个存入的数据类型不同就很阴
         */
        String key = keyPrefix + id;
        // 1.从redis查询商铺缓存
        String json = stringRedisTemplate.opsForValue().get(key);
        // 2.判断是否存在
        if (StrUtil.isBlank(json)&&json!=null) {
            RedisData redisData = JSONUtil.toBean(json, RedisData.class);
            LocalDateTime expireTime = redisData.getExpireTime();
            if (expireTime!=null&&expireTime.isAfter(LocalDateTime.now()))return queryWithMutex(keyPrefix,id,type,dbFallback,time,unit);
        }
        // 4.命中，需要先把json反序列化为对象
        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        R r = JSONUtil.toBean((JSONObject) redisData.getData(), type);
        LocalDateTime expireTime = redisData.getExpireTime();
        //判断是否过期
        //没过期直接返回
        if (expireTime!=null&&expireTime.isAfter(LocalDateTime.now())){
            return  r;
        }
        //过期就先获取锁
        // 6.1.获取互斥锁
        String lockKey = LOCK_SHOP_KEY + id;
        boolean isLock = getLock(lockKey);
        // 6.2.判断是否获取锁成功
        if (isLock){
        //    创建一个大小为10得线程池 异步查询后台 不阻塞业务
            CACHE_REBUILD_EXECUTOR.submit(()->{
                try{
                    R newR= dbFallback.apply(id);
                    if (newR==null){
                        this.setWithLogicalExpire(key, "", time, unit);
                    }
                    this.setWithLogicalExpire(key, newR, time, unit);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }finally{
                    unlock(lockKey);
                }
            });
        }
        //直接返回旧数据
        return r;
    }

    private <R> void setWithLogicalExpire(String key, R newR, Long time, TimeUnit unit) {
        RedisData redisData=new RedisData();
        redisData.setData(newR);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
        //不写过期时间 过期时间写在redis字段中 如果字段过期任然能访问redis而不是直接击穿到mysql
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData),time);
    }
}
