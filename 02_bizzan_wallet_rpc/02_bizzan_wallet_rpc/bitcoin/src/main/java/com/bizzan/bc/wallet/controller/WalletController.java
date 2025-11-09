package com.bizzan.bc.wallet.controller;

import com.bizzan.bc.wallet.service.AccountService;
import com.bizzan.bc.wallet.util.MessageResult;
import com.spark.blockchain.rpcclient.Bitcoin;
import com.spark.blockchain.rpcclient.BitcoinException;
import com.spark.blockchain.rpcclient.BitcoinRPCClient;
import com.spark.blockchain.rpcclient.BitcoinUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/rpc")
public class WalletController {
    @Autowired
    private BitcoinRPCClient rpcClient;
    
    private Logger logger = LoggerFactory.getLogger(WalletController.class);
    @Autowired
    private AccountService accountService;
    
    @GetMapping("height")
    public MessageResult getHeight(){
        try {
            int height =rpcClient.getBlockCount();
            MessageResult result = new MessageResult(0,"success");
            result.setData(height - 1);
            return result;
        }
        catch (Exception e){
            e.printStackTrace();
            return MessageResult.error(500,"查询失败,error:"+e.getMessage());
        }
    }

    @GetMapping("address/{account}")
    public MessageResult getNewAddress(@PathVariable String account){
        logger.info("create new address :"+account);
        try {
            String address = rpcClient.getNewAddress(account);
            accountService.saveOne(account,address);
            MessageResult result = new MessageResult(0,"success");
            result.setData(address);
            return result;
        }
        catch (BitcoinException e){
            e.printStackTrace();
            return MessageResult.error(500,"rpc error:"+e.getMessage());
        }
    }

    @GetMapping({"transfer","withdraw"})
    public MessageResult withdraw(String address, BigDecimal amount,BigDecimal fee){
        logger.info("withdraw:address={},amount={},fee={}",address,amount,fee);
        if(amount.compareTo(BigDecimal.ZERO) <= 0){
            return MessageResult.error(500,"额度须大于0");
        }
        try {
            String txid = BitcoinUtil.sendTransaction(rpcClient,address,amount,fee);
            MessageResult result = new MessageResult(0,"success");
            result.setData(txid);
            return result;
        }
        catch (Exception e){
            e.printStackTrace();
            return MessageResult.error(500,"error:"+e.getMessage());
        }
    }

    @GetMapping("sendfrom")
    public MessageResult sendFrom(String fromAddress, String toAddress, BigDecimal amount, BigDecimal fee) {
        logger.info("sendFrom:from={},to={},amount={},fee={}", fromAddress, toAddress, amount, fee);
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            return MessageResult.error(500, "额度须大于0");
        }
        try {
            String txid = BitcoinUtil.sendTransaction(rpcClient, fromAddress, toAddress, amount, fee);
            MessageResult result = new MessageResult(0, "success");
            result.setData(txid);
            return result;
        } catch (Exception e) {
            e.printStackTrace();
            return MessageResult.error(500, "error:" + e.getMessage());
        }
    }

    @GetMapping("balance")
    public MessageResult balance(){
        try {
            BigDecimal balance = new BigDecimal(rpcClient.getBalance());

            MessageResult result = new MessageResult(0,"success");
            result.setData(balance);
            return result;
        }
        catch (Exception e){
            e.printStackTrace();
            return MessageResult.error(500,"error:"+e.getMessage());
        }
    }

    @GetMapping("balance/{address}")
    public MessageResult balance(@PathVariable String address){
        try {
            // To get the true balance of a specific address, we must sum its unspent transaction outputs (UTXOs).
            // 'getReceivedByAddress' only shows the total funds ever received, not accounting for spent funds.
            List<Bitcoin.Unspent> unspents = rpcClient.listUnspent(1, 9999999, new String[]{address});
            BigDecimal balance = BigDecimal.ZERO;
            for (Bitcoin.Unspent unspent : unspents) {
                balance = balance.add(unspent.amount());
            }
            MessageResult result = new MessageResult(0,"success");
            result.setData(balance);
            return result;
        }
        catch (Exception e){
            e.printStackTrace();
            return MessageResult.error(500,"error:"+e.getMessage());
        }
    }
}
