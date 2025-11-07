

## ETH



### 核心结论

`eth` 和 `eth-support` 两个模块是一个典型的**分层设计**思想的体现：

*   **`eth-support`** 是一个**核心服务层/基础库模块**。它封装了与以太坊区块链交互的底层细节和核心业务逻辑。
*   **`eth`** 是一个**应用层/API 模块**。它依赖于 `eth-support`，并将其功能通过 Spring Boot 应用、定时任务和 RESTful API 的形式暴露出来，构成一个完整的微服务。

简单来说，**`eth-support` 负责“怎么做”，而 `eth` 负责“何时做”和“响应谁的请求”**。

---

### `eth-support` 模块详解

**功能定位：以太坊钱包核心功能库**

这个模块不直接运行，而是作为一个依赖库（JAR包）被其他模块（如 `eth` 和 `erc-token`）使用。它提供了所有与以太坊节点交互的原子操作。

**支持的代币：**

从代码分析，`eth-support` 模块同时支持两种类型的资产：

1.  **ETH (以太币)**：以太坊的原生代币。
    *   相关方法：`getBalance`, `transfer`, `getGasPrice` 等，这些都是直接与以太坊主币相关的操作。
2.  **ERC-20 代币**：基于以太坊智能合约发行的代币。
    *   相关方法：`getTokenBalance`, `transferToken` 等。这些方法通过与指定的智能合约 (`contract.getAddress()`) 进行交互来实现代币的余额查询和转账。具体支持哪一种 ERC-20 代币，取决于运行时注入的 `Contract` 对象的配置（即合约地址）。

**主要实现 (`EthService.java`)：**

*   **钱包管理**：创建新的以太坊钱包文件 (`createNewWallet`)。
*   **余额查询**：查询 ETH 余额 (`getBalance`) 和 ERC-20 代币余额 (`getTokenBalance`)。
*   **交易发送**：封装了发送 ETH (`transfer`) 和 ERC-20 代币 (`transferToken`) 的逻辑，包括从普通地址和指定的提现热钱包转账。
*   **与节点交互**：使用 `web3j` 库与以太坊节点进行 RPC 通信。
*   **与 Etherscan 交互 (`EtherscanApi.java`)**：它还包含一个与 Etherscan API 交互的工具类，可以用来广播交易或查询日志，作为节点RPC之外的辅助手段。

---

### `eth` 模块详解

**功能定位：以太坊钱包 RPC 微服务**

这是一个可以独立运行的 Spring Boot 应用程序。它将 `eth-support` 模块提供的核心功能包装成一个完整的、可对外提供服务的微服务。

**支持的代币：**

这个模块专门用于处理 **ETH (以太币)**。

虽然它依赖的 `eth-support` 模块也支持 ERC-20，但在 `eth` 模块自身的代码中（如 `EthWatcher` 和 `WalletController`），处理的都是 ETH 的主币交易（通过检查 `transaction.getValue()`），没有调用与 ERC-20 相关的方法。

**主要实现：**

*   **应用入口 (`WalletApplication.java`)**：标准的 Spring Boot 启动类，启用了 Eureka 客户端和定时任务。
*   **区块监控 (`EthWatcher.java`)**：
    *   这是一个核心的定时任务。
    *   它会定期从以太坊节点获取最新的区块。
    *   遍历区块中的每一笔交易，检查交易的接收方 (`to` 地址) 是否是系统内用户的地址。
    *   如果是，就判定为一笔充值，记录下来并触发后续的到账逻辑。
    *   它直接调用 `ethService.syncAddressBalance` 来更新数据库中的余额。
*   **API 接口 (`WalletController.java`)**：
    *   将 `eth-support` 中的功能暴露为 RESTful API。
    *   例如，`/rpc/address/{account}` 接口调用 `ethService.createNewWallet` 来创建地址。
    *   `/rpc/withdraw` 接口调用 `ethService.transferFromWithdrawWallet` 来执行提现。
    *   `/rpc/balance/{address}` 接口调用 `ethService.getBalance` 来查询余额。

---

### 区别总结

| 特性         | `eth-support`                               | `eth`                                                       |
| :----------- | :------------------------------------------ | :---------------------------------------------------------- |
| **定位**     | 核心功能库 (Library)                        | 完整的微服务应用 (Application)                              |
| **运行方式** | 不能独立运行，作为依赖被其他模块引用        | 可以独立打包并运行的 Spring Boot 应用                       |
| **支持代币** | **ETH 和 ERC-20 代币** (具体代币由配置决定) | **仅处理 ETH**                                              |
| **主要职责** | 封装与以太坊节点交互的底层细节和原子操作    | 编排业务流程（如区块监控、充值判断），并提供对外 API 接口   |
| **依赖关系** | 被 `eth` 模块依赖                           | 依赖于 `eth-support` 模块                                   |
| **核心代码** | `EthService.java` (核心业务逻辑)            | `EthWatcher.java` (定时任务), `WalletController.java` (API) |

这种分层设计使得代码结构清晰，易于维护和扩展。例如，当需要支持一种新的 ERC-20 代币时，很可能只需要创建一个新的应用模块（类似于 `eth`），然后复用 `eth-support` 提供的核心功能，而无需修改 `eth-support` 本身。





## eth配置

### **配置方案**

`eth` 钱包服务通过其配置文件 `application.properties` 来管理所有外部连接和关键参数。要将其指向以太坊测试网络，核心是修改其中定义以太坊节点 RPC 地址的属性。

*   **目标文件**: `eth/src/main/resources/application.properties`
*   **目标属性**: `coin.rpc`
*   **方案**: 将 `coin.rpc` 属性的当前值（一个内网 IP 地址）替换为您从第三方服务商获取的公共以太坊测试网 RPC 端点 URL。

### **实际操作步骤**

请按照以下步骤操作，即可完成配置：

**第一步：获取以太坊测试网 RPC 端点**

1.  选择一个节点服务提供商，例如 [Infura](https://infura.io/) 或 [QuickNode](https://www.quicknode.com/)。
2.  在该平台注册一个免费账户。
3.  创建一个新的项目或应用，在选择网络时，请选择一个以太坊测试网，例如 **Sepolia** (目前推荐) 或 **Goerli**。
4.  平台将为您生成一个专属的 HTTPS RPC 端点 URL。它看起来会像这样：
    *   `https://sepolia.infura.io/v3/YOUR_UNIQUE_API_KEY`
    *   `https://your-endpoint-name.ethereum-sepolia.quiknode.pro/YOUR_UNIQUE_API_KEY/`

**第二步：修改配置文件**

1. 在您的项目中，找到并打开文件：`eth/src/main/resources/application.properties`。

2. 定位到第 21 行，您会看到：

   ```properties
   coin.rpc=http://172.22.0.14:8051
   ```

3. 将这一行的值**替换**为您在第一步中获取到的完整测试网 RPC 端点 URL。例如：

   ```properties
   coin.rpc=https://sepolia.infura.io/v3/YOUR_UNIQUE_API_KEY
   ```

**第三步：调整相关配置（重要）**

在同一个文件中，您可能还需要调整钱包文件路径以适应您的本地开发环境。

1. 定位到第 24 行：

   ```properties
   coin.keystore-path=/mnt/data/keystore
   ```

2. 这个路径 `/mnt/data/keystore` 可能在您的本地电脑上不存在。请将其修改为您本地一个**实际存在**的目录路径，用来存放程序生成的以太坊钱包文件。例如，在 Windows 上可以修改为：

   ```properties
   coin.keystore-path=D:/my-eth-wallets/keystore
   ```

   或在 macOS/Linux 上：

   ```properties
   coin.keystore-path=/Users/your-username/my-eth-wallets/keystore
   ```

**第四步：重启应用程序**

1.  保存您对 `application.properties` 文件所做的修改。
2.  如果您正在运行 `eth` 模块，请将其停止。
3.  **重新启动** `eth` 模块的 Spring Boot 应用程序。

完成以上步骤后，您的 `eth` 服务就会使用新的配置，成功连接到以太坊测试网络，并将新生成的钱包文件保存在您指定的本地路径下。





## 以太坊测试网完整操作指南：创建钱包、获取测试ETH并实现转账

### 一、创建以太坊测试网钱包并获取私钥

#### 1. 使用 MetaMask 创建钱包（推荐）

**步骤：**

1. **下载安装**：从官方应用商店下载 MetaMask 浏览器扩展
2. **创建钱包**：点击"创建钱包"，设置强密码（至少8位，包含大小写字母和数字）
3. **备份助记词**：**务必手写抄下12个助记词**，不要截图或存储电子设备
4. **获取地址**：创建完成后会显示以太坊地址（以0x开头）
5. **导出私钥**：在账户详情中点击"导出私钥"，输入密码后获取私钥

#### 2. 使用 Web3j 编程创建钱包（Java）

```
import org.web3j.crypto.Credentials;
import org.web3j.crypto.WalletUtils;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.http.HttpService;
import org.web3j.utils.Convert;
import java.math.BigDecimal;
import java.math.BigInteger;

public class EthereumWalletCreator {
    
    // 创建新钱包并获取私钥
    public static void createWallet() throws Exception {
        // 生成新钱包文件
        String password = "your-strong-password";
        String walletFileName = WalletUtils.generateNewWalletFile(password, new File("/path/to/wallet/dir"), false);
        
        // 加载钱包获取凭证
        Credentials credentials = WalletUtils.loadCredentials(password, "/path/to/wallet/dir/" + walletFileName);
        
        System.out.println("钱包地址: " + credentials.getAddress());
        System.out.println("私钥: " + credentials.getEcKeyPair().getPrivateKey().toString(16));
    }
}
```

### 二、获取免费测试ETH

#### 1. 推荐测试网水龙头

**Sepolia 测试网（官方推荐）**：

- **Alchemy Sepolia Faucet**：https://sepoliafaucet.com/（需要登录Alchemy账户）
- **Infura Sepolia Faucet**：https://www.infura.io/faucet/sepolia（每天可领取0.5 ETH）
- **QuickNode Sepolia Faucet**：https://faucet.quicknode.com/ethereum/sepolia

**操作步骤：**

1. 将MetaMask切换到Sepolia测试网络
2. 复制钱包地址
3. 访问水龙头网站，粘贴地址并完成验证
4. 等待几分钟，测试ETH将到账

#### 2. 其他获取方式

**通过挖矿获取**：

- **Sepolia PoW Faucet**：https://sepolia-faucet.pk910.de/
- 输入钱包地址，点击"Start Mining"进行工作量证明计算
- 完成后领取测试ETH

### 三、Java实现以太坊转账操作

#### 1. 项目依赖配置

```
<dependency>
    <groupId>org.web3j</groupId>
    <artifactId>core</artifactId>
    <version>5.0.0</version>
</dependency>
```

#### 2. 查询余额功能

```
public class EthereumService {
    private static final Web3j web3j = Web3j.build(new HttpService("https://sepolia.infura.io/v3/YOUR_PROJECT_ID"));

    // 查询ETH余额
    public static BigDecimal getETHBalance(String address) throws Exception {
        BigInteger balanceInWei = web3j.ethGetBalance(address, DefaultBlockParameterName.LATEST)
                .send()
                .getBalance();
        return Convert.fromWei(new BigDecimal(balanceInWei), Convert.Unit.ETHER);
    }
}
```

#### 3. ETH转账功能

```
public class EthereumTransfer {
    
    // ETH转账
    public static String transferETH(String privateKey, String toAddress, BigDecimal amount) throws Exception {
        // 创建凭证
        Credentials credentials = Credentials.create(privateKey);
        
        // 获取nonce
        BigInteger nonce = web3j.ethGetTransactionCount(credentials.getAddress(), DefaultBlockParameterName.LATEST)
                .send()
                .getTransactionCount();
        
        // 构建交易
        RawTransaction rawTransaction = RawTransaction.createEtherTransaction(
                nonce,
                DefaultGasProvider.GAS_PRICE,
                DefaultGasProvider.GAS_LIMIT,
                toAddress,
                Convert.toWei(amount, Convert.Unit.ETHER).toBigInteger()
        );
        
        // 签名交易
        byte[] signedMessage = TransactionEncoder.signMessage(rawTransaction, credentials);
        String hexValue = Numeric.toHexString(signedMessage);
        
        // 发送交易
        EthSendTransaction ethSendTransaction = web3j.ethSendRawTransaction(hexValue).send();
        
        return ethSendTransaction.getTransactionHash();
    }
}
```

#### 4. 完整示例代码

```
public class EthereumDemo {
    public static void main(String[] args) {
        try {
            // 1. 查询余额
            String myAddress = "0xYourAddress";
            BigDecimal balance = EthereumService.getETHBalance(myAddress);
            System.out.println("当前余额: " + balance + " ETH");
            
            // 2. 执行转账
            String privateKey = "your-private-key";
            String toAddress = "0xRecipientAddress";
            BigDecimal amount = new BigDecimal("0.001");
            
            String txHash = EthereumTransfer.transferETH(privateKey, toAddress, amount);
            System.out.println("转账成功，交易哈希: " + txHash);
            
            // 3. 等待确认后再次查询余额
            Thread.sleep(30000); // 等待30秒
            BigDecimal newBalance = EthereumService.getETHBalance(myAddress);
            System.out.println("转账后余额: " + newBalance + " ETH");
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
```

### 四、重要注意事项

#### 1. 安全提示

- **私钥安全**：私钥是访问资产的唯一凭证，务必离线存储，不要分享给任何人
- **测试环境**：所有操作应在测试网进行，避免使用主网真实资产
- **网络确认**：确保MetaMask连接到正确的测试网络（Sepolia）

#### 2. 常见问题解决

- **Gas费用不足**：确保账户有足够的测试ETH支付Gas费
- **交易失败**：检查网络连接和地址格式是否正确
- **余额不足**：通过水龙头获取更多测试ETH

#### 3. 最佳实践

- **环境隔离**：开发环境使用测试网，生产环境使用主网
- **错误处理**：添加适当的异常处理和日志记录
- **交易监控**：使用区块浏览器监控交易状态

通过以上步骤，你可以完整地实现以太坊测试网钱包创建、测试ETH获取和转账功能的Java实现。记得在开发过程中始终使用测试网环境，确保资产安全。
