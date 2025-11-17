package com.bizzan.bc.wallet.component;

import com.spark.blockchain.rpcclient.Bitcoin;
import com.spark.blockchain.rpcclient.BitcoinRPCClient;
import com.bizzan.bc.wallet.entity.Deposit;
import com.bizzan.bc.wallet.service.AccountService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class BitcoinWatcher extends Watcher{
    @Autowired
    private BitcoinRPCClient rpcClient;
    @Autowired
    private AccountService accountService;
    private Logger logger = LoggerFactory.getLogger(Watcher.class);
    @Override
    public List<Deposit> replayBlock(Long startBlockNumber, Long endBlockNumber) {
        List<Deposit> deposits = new ArrayList<Deposit>();
        try {
            for (Long blockHeight = startBlockNumber; blockHeight <= endBlockNumber; blockHeight++) {
                String blockHash = rpcClient.getBlockHash(blockHeight.intValue());
                //获取区块
                Bitcoin.Block block =  rpcClient.getBlock(blockHash);
                List<String> txids = block.tx();
                logger.info("获取区块(" + blockHeight + ")交易列表，总交易数：" + txids.size() + "");
                //遍历区块中的交易
                for(String txid:txids){
                    Bitcoin.RawTransaction transaction =  rpcClient.getRawTransaction(txid);
                    List<Bitcoin.RawTransaction.Out> outs = transaction.vOut();
                    if(outs != null) {
                        for (Bitcoin.RawTransaction.Out out : outs) {
                            if (out.scriptPubKey() != null) {
                                // 使用8位小数精度，这是比特币的标准精度（1 BTC = 1e8 satoshi）
                                BigDecimal amount = new BigDecimal(out.value()).setScale(8, BigDecimal.ROUND_HALF_UP);

                                // 跳过零金额输出（如OP_RETURN）
                                if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                                    continue;
                                }

                                String scriptType = out.scriptPubKey().type();
                                logger.info("输出脚本 - type: {}, value: {} BTC", scriptType, amount);

                                String addresses = out.scriptPubKey().address();

                                // 尝试从现有字段获取地址
                                if (addresses != null ) {
                                    logger.info("从addresses字段解析到地址: {}", addresses);
                                }
                                // 对于witness类型，使用decodescript RPC
                                else if ("witness_v0_keyhash".equals(scriptType)) {
                                    try {
                                        String scriptHex = out.scriptPubKey().hex();
                                        logger.info("尝试使用decodescript解析P2WPKH脚本: {}", scriptHex);

                                        @SuppressWarnings("unchecked")
                                        Map<String, Object> decodeResult = (Map<String, Object>) rpcClient.query("decodescript", scriptHex);
                                        logger.info("decodescript返回结果: {}", decodeResult);

                                        if (decodeResult != null) {
                                            logger.info("decodescript结果包含的keys: {}", decodeResult.keySet());

                                            if (decodeResult.containsKey("addresses")) {
                                                @SuppressWarnings("unchecked")
                                                List<String> decodedAddresses = (List<String>) decodeResult.get("addresses");
                                                logger.info("decodescript解析到的addresses: {}", decodedAddresses);

                                                if (decodedAddresses != null && !decodedAddresses.isEmpty()) {
                                                    addresses = decodedAddresses.get(0);
                                                    logger.info("通过decodescript解析到P2WPKH地址: {}", addresses);
                                                }
                                            } else {
                                                logger.warn("decodescript结果中没有addresses字段");
                                            }
                                        } else {
                                            logger.warn("decodescript返回null结果");
                                        }
                                    } catch (Exception e) {
                                        logger.warn("使用decodescript解析P2WPKH地址失败: {}", e.getMessage(), e);
                                    }
                                }

                                if (addresses != null) {
                                    logger.info("检测到地址: {} 金额: {} BTC", addresses, amount);
                                    if (accountService.isAddressExist(addresses)) {
                                        logger.info("发现充值地址(" + addresses + ")，充值金额：" + amount + " BTC");
                                        Deposit deposit = new Deposit();
                                        deposit.setTxid(transaction.txId());
                                        deposit.setBlockHeight((long) block.height());
                                        deposit.setBlockHash(transaction.blockHash());
                                        deposit.setAmount(amount);
                                        deposit.setAddress(addresses);
                                        deposit.setTime(transaction.time());
                                        deposits.add(deposit);
                                    } else {
                                        logger.info("地址 {} 不在监控列表中", addresses);
                                    }
                                } else {
                                    logger.warn("无法解析地址，脚本类型: {}, hex: {}", scriptType, out.scriptPubKey().hex());
                                }
                            }
                        }
                    }
                }
            }
        }
        catch (Exception e){
            e.printStackTrace();
            return null;
        }
        return deposits;
    }



    @Override
    public Long getNetworkBlockHeight() {
        try {
            return Long.valueOf(rpcClient.getBlockCount());
        }
        catch (Exception e){
            e.printStackTrace();
            return 0L;
        }
    }
}
