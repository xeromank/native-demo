package com.example.nativedemo.config

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import javax.sql.DataSource

@Configuration
class DataSourceConfig {
//    @Bean
//    fun dataSource(
//        @Value("\${spring.datasource.url}") url: String,
//        @Value("\${spring.datasource.username}") username: String,
//        @Value("\${spring.datasource.password}") password: String
//    ): DataSource {
//        val config = HikariConfig().apply {
//            jdbcUrl = url
//            this.username = username
//            this.password = password
//            maximumPoolSize = 10
//            minimumIdle = 5
//            idleTimeout = 30000
//            connectionTimeout = 20000
//            // 네이티브 이미지에서 최적화를 위한 설정
//            isRegisterMbeans = false
//        }
//        return HikariDataSource(config)
//    }
}
