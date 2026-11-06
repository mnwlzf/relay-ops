package com.relayops.infrastructure.config;

import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@MapperScan(basePackages = "com.relayops", annotationClass = Mapper.class)
public class MybatisPlusConfiguration {
}
