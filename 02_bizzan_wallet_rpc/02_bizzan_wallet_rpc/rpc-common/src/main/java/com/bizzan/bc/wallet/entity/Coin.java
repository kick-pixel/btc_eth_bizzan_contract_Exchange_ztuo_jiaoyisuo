package com.bizzan.bc.wallet.entity;


import java.math.BigDecimal;
import java.math.BigInteger;

import lombok.Data;

@Data
public class Coin {
    private String name;
    private String unit;
    private String rpc;
    private String keystorePath;
    private BigDecimal defaultMinerFee;
    private String withdrawAddress;
    private String withdrawWallet;
    private String withdrawWalletPassword;
    private BigDecimal minCollectAmount;
    private BigInteger gasLimit;
    private BigDecimal gasSpeedUp = BigDecimal.ONE;
    private BigDecimal rechargeMinerFee;
    private String ignoreFromAddress;
    private String masterAddress;

    public String getName() {
        return name;
    }

    public String getUnit() {
        return unit;
    }

    public String getRpc() {
        return rpc;
    }

    public String getKeystorePath() {
        return keystorePath;
    }

    public BigDecimal getDefaultMinerFee() {
        return defaultMinerFee;
    }

    public String getWithdrawAddress() {
        return withdrawAddress;
    }

    public String getWithdrawWallet() {
        return withdrawWallet;
    }

    public String getWithdrawWalletPassword() {
        return withdrawWalletPassword;
    }

    public BigDecimal getMinCollectAmount() {
        return minCollectAmount;
    }

    public BigInteger getGasLimit() {
        return gasLimit;
    }

    public BigDecimal getGasSpeedUp() {
        return gasSpeedUp;
    }

    public BigDecimal getRechargeMinerFee() {
        return rechargeMinerFee;
    }

    public String getIgnoreFromAddress() {
        return ignoreFromAddress;
    }

    public String getMasterAddress() {
        return masterAddress;
    }
}
