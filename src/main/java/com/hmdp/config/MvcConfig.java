package com.hmdp.config;

import com.hmdp.utils.LoginINterceptor;
import com.hmdp.utils.RefreshIntercoptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class MvcConfig implements WebMvcConfigurer {
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        //    token刷新拦截器
        registry.addInterceptor(new RefreshIntercoptor()).order(0);
        //    添加登录拦截器 路径有的为不需要token得 第二个执行
        registry.addInterceptor(new LoginINterceptor())
                .excludePathPatterns(
                        "/shop/**",
                        "/voucher/**",
                        "/shop-type/**",
                        "/upload/**",
                        "/blog/hot",
                        "/user/code",
                        "/user/login").order(1);

    }

}
