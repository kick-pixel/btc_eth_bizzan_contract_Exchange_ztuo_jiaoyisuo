package com.bizzan.bitrade.component;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.bizzan.bitrade.entity.Coin;
import com.bizzan.bitrade.entity.CoinThumb;
import com.bizzan.bitrade.processor.CoinProcessor;
import com.bizzan.bitrade.processor.CoinProcessorFactory;
import com.bizzan.bitrade.service.CoinService;
import com.bizzan.bitrade.service.ExchangeCoinService;
import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.JsonNode;
import com.mashape.unirest.http.Unirest;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 币种汇率管理
 */
@Component
@Slf4j
@ToString
public class CoinExchangeRate {
    @Getter
    @Setter
    private BigDecimal usdCnyRate = new BigDecimal("6.90");
    
    @Getter
    @Setter
    private BigDecimal usdtCnyRate = new BigDecimal("7.00");
    
    @Getter
    @Setter
    private BigDecimal usdJpyRate = new BigDecimal("110.02");
    @Getter
    @Setter
    private BigDecimal usdHkdRate = new BigDecimal("7.8491");
    @Getter
    @Setter
    private BigDecimal sgdCnyRate = new BigDecimal("4.77");
    @Setter
    private CoinProcessorFactory coinProcessorFactory;

    @Autowired
    private CoinService coinService;
    @Autowired
    private ExchangeCoinService exCoinService;


    public BigDecimal getUsdRate(String symbol) {
        log.info("CoinExchangeRate getUsdRate unit = " + symbol);
        if ("USDT".equalsIgnoreCase(symbol)) {
            log.info("CoinExchangeRate getUsdRate unit = USDT  ,result = ONE");
            return BigDecimal.ONE;
        } else if ("CNY".equalsIgnoreCase(symbol)) {
            log.info("CoinExchangeRate getUsdRate unit = CNY  ,result : 1 divide {}", this.usdtCnyRate);
            BigDecimal bigDecimal = BigDecimal.ONE.divide(usdtCnyRate, 4,BigDecimal.ROUND_DOWN).setScale(4, BigDecimal.ROUND_DOWN);
            return bigDecimal;
        }else if ("BITCNY".equalsIgnoreCase(symbol)) {
            BigDecimal bigDecimal = BigDecimal.ONE.divide(usdCnyRate, 4,BigDecimal.ROUND_DOWN).setScale(4, BigDecimal.ROUND_DOWN);
            return bigDecimal;
        } else if ("ET".equalsIgnoreCase(symbol)) {
            BigDecimal bigDecimal = BigDecimal.ONE.divide(usdCnyRate, 4,BigDecimal.ROUND_DOWN).setScale(4, BigDecimal.ROUND_DOWN);
            return bigDecimal;
        } else if ("JPY".equalsIgnoreCase(symbol)) {
            BigDecimal bigDecimal = BigDecimal.ONE.divide(usdJpyRate, 4,BigDecimal.ROUND_DOWN).setScale(4, BigDecimal.ROUND_DOWN);
            return bigDecimal;
        }else if ("HKD".equalsIgnoreCase(symbol)) {
            BigDecimal bigDecimal = BigDecimal.ONE.divide(usdHkdRate, 4,BigDecimal.ROUND_DOWN).setScale(4, BigDecimal.ROUND_DOWN);
            return bigDecimal;
        }
        String usdtSymbol = symbol.toUpperCase() + "/USDT";
        String btcSymbol = symbol.toUpperCase() + "/BTC";
        String ethSymbol = symbol.toUpperCase() + "/ETH";

        if (coinProcessorFactory != null) {
            if (coinProcessorFactory.containsProcessor(usdtSymbol)) {
                log.info("Support exchange coin = {}", usdtSymbol);
                CoinProcessor processor = coinProcessorFactory.getProcessor(usdtSymbol);
                if(processor == null) {
                	return BigDecimal.ZERO;
                }
                CoinThumb thumb = processor.getThumb();
                if(thumb == null) {
                	log.info("Support exchange coin thumb is null", thumb);
                	return BigDecimal.ZERO;
                }
                return thumb.getUsdRate();
            } else if (coinProcessorFactory.containsProcessor(btcSymbol)) {
                log.info("Support exchange coin = {}/BTC", btcSymbol);
                CoinProcessor processor = coinProcessorFactory.getProcessor(btcSymbol);
                if(processor == null) {
                	return BigDecimal.ZERO; 
                }
                CoinThumb thumb = processor.getThumb();
                if(thumb == null) {
                	log.info("Support exchange coin thumb is null", thumb);
                	return BigDecimal.ZERO;
                }
                return thumb.getUsdRate();
            } else if (coinProcessorFactory.containsProcessor(ethSymbol)) {
                log.info("Support exchange coin = {}/ETH", ethSymbol);
                CoinProcessor processor = coinProcessorFactory.getProcessor(ethSymbol);
                if(processor == null) {
                	return BigDecimal.ZERO; 
                }
                CoinThumb thumb = processor.getThumb();
                if(thumb == null) {
                	log.info("Support exchange coin thumb is null", thumb);
                	return BigDecimal.ZERO;
                }
                return thumb.getUsdRate();
            } else {
                return getDefaultUsdRate(symbol);
            }
        } else {
            return getDefaultUsdRate(symbol);
        }
    }

    /**
     * 获取币种设置里的默认价格
     *
     * @param symbol
     * @return
     */
    public BigDecimal getDefaultUsdRate(String symbol) {
        Coin coin = coinService.findByUnit(symbol);
        if (coin != null) {
            return coin.getUsdRate();
        } else {
            return BigDecimal.ZERO;
        }
    }

    public BigDecimal getCnyRate(String symbol) {
        if ("CNY".equalsIgnoreCase(symbol)) {
            return BigDecimal.ONE;
        } else if("ET".equalsIgnoreCase(symbol)){
            return BigDecimal.ONE;
        }
        return getUsdRate(symbol).multiply(usdtCnyRate).setScale(2, RoundingMode.DOWN);
    }

    public BigDecimal getJpyRate(String symbol) {
        if ("JPY".equalsIgnoreCase(symbol)) {
            return BigDecimal.ONE;
        }
        return getUsdRate(symbol).multiply(usdJpyRate).setScale(2, RoundingMode.DOWN);
    }

    public BigDecimal getHkdRate(String symbol) {
        if ("HKD".equalsIgnoreCase(symbol)) {
            return BigDecimal.ONE;
        }
        return getUsdRate(symbol).multiply(usdHkdRate).setScale(2, RoundingMode.DOWN);
    }

    /**
     * 每5分钟同步一次价格
     *
     */
    
    @Scheduled(cron = "0 */5 * * * *")
    public void syncUsdtCnyPrice() {
        try {
            // 使用币安P2P接口获取USDT价格
            String url = "https://p2p.binance.com/bapi/c2c/v2/friendly/c2c/adv/search";
            JSONObject params = new JSONObject();
            params.put("page", 1);
            params.put("rows", 1);
            params.put("payTypes", new JSONArray());
            params.put("asset", "USDT");
            params.put("tradeType", "SELL");
            params.put("fiat", "CNY");
            params.put("publisherType", null);

            HttpResponse<JsonNode> response = Unirest.post(url)
                    .header("Content-Type", "application/json")
                    .body(params.toJSONString())
                    .asJson();

            if (response.getStatus() == 200) {
                JSONObject body = JSON.parseObject(response.getBody().toString());
                if (body.getBooleanValue("success")) {
                    JSONArray data = body.getJSONArray("data");
                    if (data.size() > 0) {
                        JSONObject ad = data.getJSONObject(0).getJSONObject("adv");
                        BigDecimal price = ad.getBigDecimal("price");
                        setUsdtCnyRate(price.setScale(2, RoundingMode.HALF_UP));
                        log.info("Successfully fetched USDT/CNY rate from Binance P2P: {}", price);
                        return;
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to fetch USDT/CNY rate from Binance P2P", e);
        }
        log.warn("Failed to fetch USDT/CNY rate.");
    }
    
    /**
     * 每30分钟同步一次价格
     *
     */
    
    @Scheduled(cron = "0 */30 * * * *")
    public void syncPrice() {
        try {
            // 使用免费汇率API
            String url = "https://open.er-api.com/v6/latest/USD";
            HttpResponse<JsonNode> resp = Unirest.get(url).asJson();
            log.info("forex result:{}", resp.getBody());
            JSONObject ret = JSON.parseObject(resp.getBody().toString());

            if ("success".equals(ret.getString("result"))) {
                JSONObject rates = ret.getJSONObject("rates");
                if (rates.containsKey("CNY")) {
                    setUsdCnyRate(rates.getBigDecimal("CNY").setScale(2, RoundingMode.DOWN));
                    log.info("Updated USD/CNY rate: {}", usdCnyRate);
                }
                if (rates.containsKey("JPY")) {
                    setUsdJpyRate(rates.getBigDecimal("JPY").setScale(2, RoundingMode.DOWN));
                    log.info("Updated USD/JPY rate: {}", usdJpyRate);
                }
            }
        } catch (Exception e) {
            log.error("Failed to fetch forex rates", e);
        }
    }
}
