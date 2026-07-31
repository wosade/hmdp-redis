package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;
import static com.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;


/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result sendCode(String phone) {
    //     先校验手机是否合法
        if (RegexUtils.isPhoneInvalid(phone)){
            return Result.fail("手机号格式错误");
        }
    //     生成随机验证码 6位
        String random= RandomUtil.randomNumbers(6);
    //     保存验证码到session
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY+phone,random,LOGIN_CODE_TTL, TimeUnit.MINUTES);
    //    调用发送验证码api
        log.info("发送验证码{}",random);
        return Result.ok(random);
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        // 1.校验手机号
        if (RegexUtils.isPhoneInvalid(loginForm.getPhone())){
            return  Result.fail("手机号错误");
        }
    //     校验验证码
        String random=stringRedisTemplate.opsForValue().get(LOGIN_USER_KEY+loginForm.getPhone());
        // 如果验证码不正确
        if (random==null||!random.equals(loginForm.getCode())){
            return  Result.fail("验证码不正确");
        }
        //查询用户是否存在
        User user=query().eq("phone",loginForm.getPhone()).one();
        log.info("用户详细信息:{}",user);
        if (user==null){
            //不存在就创建user
             user=CreateUserWithPhone(loginForm.getPhone());
        }
        String token= UUID.randomUUID().toString();
        //将user转成map对象存储
        UserDTO userDTO= BeanUtil.copyProperties(user,
                UserDTO.class);
        Map<String,Object> map=BeanUtil.beanToMap(userDTO,new HashMap<>(),
                CopyOptions.create()
                        .setIgnoreNullValue(true).
                        setFieldValueEditor((fieldName, fieldValue) ->
                                fieldValue.toString())
        );
        //通过token存储user信息
        String tokenKey=LOGIN_USER_KEY+token;
        stringRedisTemplate.opsForHash().putAll(tokenKey,map);
        return Result.ok(token);
    }

    /**
     * 创建user用户
     * @param phone
     * @return
     */
    private User CreateUserWithPhone(String phone) {
        User user=User.builder().phone(phone).nickName(USER_NICK_NAME_PREFIX+RandomUtil.randomString(10)).build();
        //插入user
        save(user);
        return user;
    }
}
