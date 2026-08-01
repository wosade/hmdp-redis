package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisConstants.*;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;
import static com.hmdp.utils.RedisConstants.CACHE_SHOP_TTL;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public  class  ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {
    @Resource
    private RedisTemplate redisTemplate;

    @Resource
    private CacheClient cacheClient;

    @Override
    public Result queryBy(Long id) {
        // 解决缓存穿透 当用户查id redis没有就去查数据库 数据库也没有导致反复查数据库 数据库没有的直接让redis存空值
        //Shop shop=cacheClient.queryWithPassThrough(id,Shop.class,this::getById, CACHE_SHOP_KEY, CACHE_NULL_TTL, TimeUnit.MINUTES);

        // 互斥锁解决缓存击穿 redis里面存一个锁 多个线程进行时如果redis没有缓存就先去抢锁 其他没抢到得就休眠等待 抢到得就去将sql里面得写进数据库。其他得再通过redis查询。
        // Shop shop = cacheClient
                 //.queryWithMutex(CACHE_SHOP_KEY, id, Shop.class, this::getById, CACHE_SHOP_TTL, TimeUnit.HOURS);

        // 逻辑过期解决缓存击穿 过期时间存在redis里面 就算key过期 也会通过redis查询 不会穿透数据库 当key过期请求全部打到数据库
         Shop shop = cacheClient
                 .queryWithLogicalExpire(CACHE_SHOP_KEY, id, Shop.class, this::getById, 20L, TimeUnit.SECONDS);
        if (shop!=null)return Result.ok(shop);
        else return Result.fail("未找到信息");
    }
}
