package com.bizzan.bc.wallet.config;

import com.spark.blockchain.rpcclient.BitcoinRPCClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.MalformedURLException;

/**
 * 初始化RPC客户端
 */
@Configuration
public class RpcClientConfig {
    private Logger logger = LoggerFactory.getLogger(RpcClientConfig.class);

    @Bean
    public BitcoinRPCClient setClient(@Value("${coin.rpc}") String uri){
        try {
            logger.info("uri={}",uri);
            return new BitcoinRPCClient(uri);
        } catch (MalformedURLException e) {
            logger.error("init wallet failed, invalid rpc uri: {}", uri, e);
            e.printStackTrace();
            return null;
        }
    }
}
