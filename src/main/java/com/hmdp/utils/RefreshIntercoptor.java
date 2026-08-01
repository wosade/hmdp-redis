package com.hmdp.utils;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.swing.text.html.parser.Entity;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.LOGIN_USER_KEY;

public class RefreshIntercoptor implements HandlerInterceptor {
    private final StringRedisTemplate stringRedisTemplate;
    public RefreshIntercoptor(StringRedisTemplate stringRedisTemplate){
        this.stringRedisTemplate=stringRedisTemplate;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 1.获取请求头中的token
        String token = request.getHeader("authorization");
        //如果用户未登录则不拦截刷新token
        if (StrUtil.isBlank(token)){
            return true;
        }
        //查询用户如果为不存在就放行
        String Tokenkey=LOGIN_USER_KEY+token;
        Map<Object, Object> userMap = stringRedisTemplate.opsForHash().entries(Tokenkey);
        // 判断用户是否存在
        if (userMap.isEmpty()) {
            return true;  // 用户不存在,放行
        }

        // 将 Map 转成 UserDTO
        UserDTO userDTO = BeanUtil.fillBeanWithMap(userMap, new UserDTO(), false);
        UserHolder.saveUser(userDTO);
        //将token时限刷新
        stringRedisTemplate.expire(Tokenkey,RedisConstants.LOGIN_CODE_TTL, TimeUnit.DAYS);
        return true;
    }
}
