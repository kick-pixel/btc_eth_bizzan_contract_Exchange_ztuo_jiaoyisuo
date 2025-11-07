package com.bizzan.bc.wallet.entity;

import java.math.BigInteger;

import org.apache.commons.lang3.StringUtils;

import com.bizzan.bc.wallet.util.EthConvert;

import lombok.Data;

@Data
public class Contract {
    //合约精度
    private String decimals;
    //合约地址
    private String address;
    private BigInteger gasLimit;
    private String eventTopic0;
    public EthConvert.Unit getUnit(){
        if(StringUtils.isEmpty(decimals))return EthConvert.Unit.ETHER;
        else return EthConvert.Unit.fromString(decimals);
    }

    public String getDecimals() {
        return decimals;
    }

    public String getAddress() {
        return address;
    }

    public BigInteger getGasLimit() {
        return gasLimit;
    }

    public String getEventTopic0() {
        return eventTopic0;
    }
}
