package com.colonel.saas.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 密码编码器配置。
 * <p>
 * 为 Spring Security 提供 {@link PasswordEncoder} 实现，使用 BCrypt 算法进行密码哈希。
 * BCrypt 是当前业界推荐的密码哈希算法，具有自适应成本因子，可抵御暴力破解攻击。
 * </p>
 *
 * <p>使用场景：</p>
 * <ul>
 *   <li>用户注册时密码加密存储</li>
 *   <li>用户登录时密码比对验证</li>
 *   <li>密码修改时的新密码加密</li>
 * </ul>
 *
 * @see SecurityConfig
 */
@Configuration
public class PasswordConfig {

    /**
     * 创建 BCrypt 密码编码器 Bean。
     * <p>
     * BCrypt 默认成本因子为 10（2^10 次迭代），每次生成的哈希值都包含随机盐，
     * 同一明文密码会生成不同的哈希值，安全性高于 MD5/SHA 等固定盐方案。
     * </p>
     *
     * @return BCryptPasswordEncoder 实例
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
