### BitCoin

该项目是一个基于Java和Spring Boot构建的比特币钱包后端服务。它采用模块化设计，将功能划分为三个主要部分：

1.  **`bitcoin-rpc`**: 底层的比特币RPC客户端，负责与比特币核心节点进行通信。
2.  **`rpc-common`**: 通用的钱包功能框架，提供可扩展的区块监控、数据持久化等功能。
3.  **`bitcoin`**: 比特币钱包的具体实现，集成了前两个模块，并通过REST API对外提供服务。

---

### 1. `bitcoin-rpc` 模块

**目的**: 提供一个纯粹的Java客户端，用于通过JSON-RPC与比特币核心（bitcoind）节点进行交互。

**核心功能**:

*   **[`Bitcoin.java`](bitcoin-rpc/src/main/java/com/spark/blockchain/rpcclient/Bitcoin.java)**: 这是一个核心接口，定义了所有可用的RPC命令，例如：
    *   查询区块链信息 (`getBlockCount`, `getBlock`, `getBlockHash`)
    *   管理钱包地址 (`getNewAddress`, `getAccountAddress`, `validateAddress`)
    *   查询余额和交易 (`getBalance`, `getTransaction`, `listTransactions`)
    *   发送交易 (`sendToAddress`, `sendRawTransaction`)
    *   处理裸交易 (`createRawTransaction`, `signRawTransaction`)
*   **[`BitcoinRPCClient.java`](bitcoin-rpc/src/main/java/com/spark/blockchain/rpcclient/BitcoinRPCClient.java)**: `Bitcoin` 接口的实现类。它处理HTTP连接、认证、构造JSON请求和解析JSON响应的全部细节。
*   **[`BitcoinAcceptor.java`](bitcoin-rpc/src/main/java/com/spark/blockchain/rpcclient/BitcoinAcceptor.java)** & **[`BitcoinPaymentListener.java`](bitcoin-rpc/src/main/java/com/spark/blockchain/rpcclient/BitcoinPaymentListener.java)**: 提供了一个事件驱动的机制来监听新的区块和收款交易。`BitcoinAcceptor` 在一个后台线程中运行，定期检查新交易并通知 `BitcoinPaymentListener` 的实现者。

**逻辑**: 该模块封装了与比特币节点通信的复杂性，为上层应用提供了一个简单易用的Java接口。开发者无需关心RPC的底层实现细节，可以直接调用Java方法来与比特币网络交互。

---

### 2. `rpc-common` 模块

**目的**: 提供一个通用的、可重用的钱包后端框架，可以轻松扩展以支持不同的加密货币。

**核心功能**:

*   **[`Watcher.java`](rpc-common/src/main/java/com/bizzan/bc/wallet/component/Watcher.java)**: 一个抽象的区块监控器。它定义了一个标准的流程：在一个循环线程中，定期获取最新的区块高度，然后扫描指定范围内的区块（`replayBlock`），并将发现的充值交易通过 `DepositEvent` 事件发布出去。
*   **[`Coin.java`](rpc-common/src/main/java/com/bizzan/bc/wallet/entity/Coin.java)**: 一个数据类，用于定义和配置一个币种所需的所有参数，如RPC地址、钱包文件路径、手续费、主地址等。
*   **数据实体 ([`Deposit.java`](rpc-common/src/main/java/com/bizzan/bc/wallet/entity/Deposit.java), `Account.java`)**: 定义了通用的数据模型，如充值记录和用户账户。
*   **服务 ([`DepositService.java`](rpc-common/src/main/java/com/bizzan/bc/wallet/service/DepositService.java), `AccountService.java`)**: 提供了与数据库（MongoDB）交互的服务，用于存储和查询充值记录及账户信息。
*   **配置 (`KafkaConfiguration.java`, `MongodbConfig.java`)**: 提供了与Kafka和MongoDB集成的标准配置。

**逻辑**: 该模块是整个钱包系统的骨架。它没有针对任何特定的币种，而是提供了一套通用的工具和流程。要支持一个新的币种，开发者只需要继承 `Watcher` 类并实现其抽象方法即可，极大地提高了代码的复用性。

---

### 3. `bitcoin` 模块

**目的**: 作为比特币钱包服务的具体实现和主应用程序。它将 `bitcoin-rpc` 和 `rpc-common` 两个模块的功能整合在一起。

**核心功能**:

*   **[`WalletRpcApplication.java`](bitcoin/src/main/java/com/bizzan/bc/wallet/WalletRpcApplication.java)**: Spring Boot应用程序的启动入口。
*   **[`RpcClientConfig.java`](bitcoin/src/main/java/com/bizzan/bc/wallet/config/RpcClientConfig.java)**: Spring配置类，用于创建和配置 `BitcoinRPCClient` 实例，使其可以被依赖注入到其他组件中。
*   **[`BitcoinWatcher.java`](bitcoin/src/main/java/com/bizzan/bc/wallet/component/BitcoinWatcher.java)**: 这是 `rpc-common` 中 `Watcher` 类的具体实现。
    *   它实现了 `getNetworkBlockHeight` 方法，通过调用 `bitcoin-rpc` 的 `getBlockCount` 来获取比特币网络的当前区块高度。
    *   它实现了 `replayBlock` 方法，在该方法中，它遍历指定高度范围内的所有区块，检查每一笔交易的输出。如果某个输出的地址存在于系统的数据库中（通过 `AccountService` 查询），则认为这是一笔充值交易，并创建一个 `Deposit` 对象。
*   **[`WalletController.java`](bitcoin/src/main/java/com/bizzan/bc/wallet/controller/WalletController.java)**: 一个REST控制器，对外提供HTTP API接口，用于执行钱包的核心操作：
    *   `GET /rpc/height`: 获取当前区块高度。
    *   `GET /rpc/address/{account}`: 为指定账户生成一个新的比特币地址。
    *   `GET /rpc/transfer` (或 `withdraw`): 从钱包发送比特币到指定地址。
    *   `GET /rpc/balance`: 查询钱包的总余额。
    *   `GET /rpc/balance/{address}`: 查询与特定地址关联的账户余额。

**逻辑**: 该模块是整个系统的业务逻辑层和入口。它通过 `BitcoinWatcher` 自动监控和处理比特币充值，并通过 `WalletController` 响应用户的操作请求。它完美地展示了如何利用 `bitcoin-rpc` 模块进行节点通信，以及如何利用 `rpc-common` 模块的框架来简化开发。

### 总结

这三个模块协同工作，构成了一个结构清晰、功能完善的比特币钱包服务。

*   `bitcoin-rpc` 负责“如何说”（与节点通信）。
*   `rpc-common` 负责“做什么”（监控、存储的通用流程）。
*   `bitcoin` 负责“具体做”（实现比特币的特定逻辑并提供服务）。

从代码结构和实现来看，该项目的功能逻辑是**正常且设计良好**的，遵循了分层和模块化的最佳实践，具有良好的可扩展性。

---

### 4. 本地开发与测试指南

本指南将介绍如何搭建和使用比特币测试网络，并将 `bitcoin` 钱包服务连接到该网络进行开发和测试。

#### 4.1 比特币网络类型简介

在进行比特币开发时，了解不同网络类型的区别至关重要。

*   **主网 (Mainnet):** 这是真实、公开的比特币网络，网络中的交易涉及真实的比特币（BTC）。所有操作都是永久且不可逆的。生产环境的应用应连接主网。
*   **测试网 (Testnet):** 这是一个公开的、用于测试目的的网络。测试网的比特币（tBTC）没有实际价值，可以通过“水龙头”（Faucet）免费获取。它的规则与主网基本相同，但区块难度较低，适合进行公开的功能测试。
*   **回归测试网 (Regtest):** 这是一个私有的、本地的测试网络。在 Regtest 模式下，你可以完全控制整个网络环境，包括瞬间生成区块（挖矿）、创建任意数量的测试币等。它没有预设的规则，区块之间也没有固定的时间间隔，非常适合用于本地开发、自动化测试和功能验证。

| 特性 | 主网 (Mainnet) | 测试网 (Testnet) | 回归测试网 (Regtest) |
| :--- | :--- | :--- | :--- |
| **目的** | 生产环境 | 公开测试 | **本地开发/私有测试** |
| **代币价值** | 真实价值 | 无价值 | 无价值 |
| **获取代币** | 购买/挖矿 | 水龙头免费获取 | **即时生成** |
| **网络环境** | 全球公开 | 全球公开 | **私有/本地** |
| **区块生成** | 约10分钟 | 约10分钟（难度可变） | **手动/按需生成** |
| **推荐用途** | 线上应用 | 模拟真实环境测试 | **开发、调试、自动化测试** |

本项目推荐使用 **Regtest** 模式进行本地开发，因为它提供了最高的灵活性和效率。

#### 4.2 搭建本地比特币测试网络 (Regtest)

项目中的 `docker` 目录下提供了一个 `docker-compose.yml` 文件，可以一键启动一个 Regtest 模式的比特币测试节点。

**`docker-compose.yml` 配置解析:**

```yaml
version: '3'

services:
  bitcoind:
    image: kylemanna/bitcoind  # 使用的 bitcoind 镜像
    container_name: bitcoin-regtest # 容器名称
    volumes:
      - ./bitcoin-data:/bitcoin/.bitcoin # 将节点数据持久化到本地
    ports:
      # RPC 端口，用于钱包服务通信
      - "18443:18443"
      # P2P 端口，用于节点间通信
      - "18444:18444"
    command:
      - "-regtest=1"          # 启动为 regtest 模式
      - "-rpcuser=user"         # RPC 用户名
      - "-rpcpassword=pass"     # RPC 密码
      - "-rpcallowip=0.0.0.0/0" # 允许所有 IP 连接 RPC
      - "-rpcbind=0.0.0.0"      # 绑定 RPC 服务到所有网络接口
      - "-txindex=1"          # 开启交易索引，以便查询任意交易
      - "-server=1"           # 启用 RPC 服务
      - "-fallbackfee=0.00001"  # 设置默认手续费，防止在 regtest 模式下因无法估算手续费而导致交易失败
```

**启动步骤:**

1.  打开终端，进入项目根目录下的 `docker` 文件夹。
2.  执行以下命令启动容器：
    ```bash
    docker-compose up -d
    ```
    该命令会以后台模式启动 `bitcoind` 服务。你可以使用 `docker ps` 命令确认 `bitcoin-regtest` 容器是否正在运行。

#### 4.3 连接 `bitcoin` 服务到测试网络

1.  **修改配置文件**:
    打开 `bitcoin/src/main/resources/application.properties` 文件。
    找到 `coin.rpc` 配置项，并将其修改为指向本地 Docker 容器的地址：
    ```properties
    coin.rpc=http://user:pass@127.0.0.1:18443/
    ```
    这里的用户名 `user` 和密码 `pass` 必须与 `docker-compose.yml` 中设置的 `rpcuser` 和 `rpcpassword` 一致。

2.  **启动 `bitcoin` 服务**:
    通过你偏好的方式启动 `bitcoin` Spring Boot 应用（例如，在 IDE 中运行 `WalletRpcApplication.java` 或在 `bitcoin` 模块目录下执行 `mvn spring-boot:run`）。

#### 4.4 在测试网络中进行操作

为了测试钱包功能，我们需要在 `regtest` 网络中进行一些基本操作，如创建钱包、挖矿以获得初始余额等。这些操作需要通过 `bitcoin-cli` 工具与容器内的 `bitcoind` 节点交互。

**进入容器执行命令:**

你可以使用以下命令进入 `bitcoin-regtest` 容器的 shell 环境：

```bash
docker exec -it bitcoin-regtest /bin/bash
```

进入后，你需要为 `bitcoin-cli` 命令手动指定 RPC 用户和密码，因为它们没有被自动配置。

**1. 创建钱包:**

新版本的 `bitcoind` 不会自动创建默认钱包，因此在执行任何钱包相关操作之前，我们必须手动创建一个。

```bash
# 在容器内执行，创建一个匿名的默认钱包
bitcoin-cli -regtest -rpcuser=user -rpcpassword=pass createwallet ""
```
**重要提示**: 由于节点支持多钱包，后续所有与钱包相关的 `bitcoin-cli` 命令都必须使用 `-rpcwallet=<wallet_name>` 参数来指定要操作的钱包。对于上面创建的匿名钱包，你需要使用 `-rpcwallet=""`。

**2. 挖矿并生成初始余额:**

`regtest` 网络的区块不会自动生成，需要手动挖矿。挖出的比特币将作为 coinbase 交易的奖励进入你的钱包。

```bash
# 在容器内执行
# 首先，获取一个新地址用于接收挖矿奖励
address=$(bitcoin-cli -regtest -rpcuser=user -rpcpassword=pass -rpcwallet="" getnewaddress)

root@213f6bb854da:/# echo $address
bcrt1qcr0jc40cjs6mur6zfcfpu0gep3f2tk6jn8t6zv

# 挖 101 个区块。比特币规定 coinbase 交易需要 100 个区块的成熟期后才能花费。
# 因此，挖 101 个块可以确保第一个区块的奖励变为可用余额。
bitcoin-cli -regtest -rpcuser=user -rpcpassword=pass -rpcwallet="" generatetoaddress 101 $address

root@213f6bb854da:/# bitcoin-cli -regtest -rpcuser=user -rpcpassword=pass -rpcwallet="" generatetoaddress 101 $address
[
  "119ebeb37b1926b52ae75a5d808bc63cbc1f6e0970eb6f10a2bb32aa8257c6c4",
  "51dc3d232a6f35a53176c1d91e02b803106b1813c03a984f81905c3c156a9c4a",
  "7bed2c9be3bd715d16cbc5e39e8b1e00f355a4823c0a9a27c147401f2292d9c6",
  "0d74633c9aa7bc8a4210f2660fef31861426bd5a92c380bcadfc37ab210dd9be",
  "679a8edf9e576345a8e3bca52a8c69333adec63080494ca9395fdf923f15b8f1",
  "0f6eb8ffdce991455ff556f9a7f1c850e31fb0a147bcd368c8a9d8615cfb12af",
  "621a3f6de7be04658de688ebbb3a46bbebebe14ca431ba472e3e29039442602b",
  "285173d268f380525e2f5e1c49ac740df2d56bfbc70d3055875fc546cdcb6737",
  "0dbfa2fbc8c4341775d8d3e16d3bb1cefed5b7768aa74547ff43dab5e6d8b696",
  "608d23560450ee3c579ca2a81792dec0be280528f91566692e250871c7120f25",
  "7f473f023152d59982bd1c3be05e86117ac62f4547cf7c10e895d0ce190e253e",
  "16491c2e49d643a368b2b62db5b4311818a48deffb5225829af3268452445526",
  "6f3329f788fd53b1f78c227197303ec46fca0920ae82f14bc67df2fcc0f79dfd",
  "584443225b2e56b72945e7c5f3df54b28440c868e10566c2863c07b581276b52",
  "6fd685e80aa195bf8d113a254039c29b0eab6adf8e9c75ff0a545834b5817cd7",
  "22eaf91bc0fd22fa49f17699cdb8b793880332424732c5cfe442dceeb7edc14f",
  "5b83ac8e775d8359043f5d092b9a044b887cea1f62c1cc76aa611af91a75dbbf",
  "6c907ec89823007c534073ebc49c9504f5da6444ac782356dccefd8867726da3",
  "0ade16907d4ddfb6852074f892641ff09b889bd6c22faac20c07e4add5301e3e",
  "534dcf47fa4156ad4de2605f08f8b46b610a54e555da32a84620ab2ce7cb93f1",
  "53f5c103e064ccab9aac3331795615b20389f7e3e1d5802c7e371725cb24dfea",
  "392063e49b5362d3db36816475b7a22ba171811af6372f0a2b85ca31b1d90d37",
  "76039029f15add90884279e0f016beb65b68e97fa86900e6229947d1f439fd8a",
  "181964a0a6f75842fa1637c8baf9a695383cd0588f8810d273d0bd114009f274",
  "57f911888c8ffbef45360e4948ae401c6f62b5a4709966ebacceb268c471900e",
  "1a6d8f8c300baa5b20b8f735058cfa28111adca2e53395f89099663a9650cfb5",
  "0ef212f7ddb26c03a3fc33fc78ba4ed4904f22464ac518ea198e09a462b388a6",
  "13bd44a1cc9ee3d7aab850d9bf612a4b313f793ea6b2f2495012fbd1d32c2418",
  "1756739511b4bfd565e38a75ff83261e855b7f574af4db41f2738092d2fedb49",
  "54e6ae2a60051335175799f272d0af48edbed7d8cde2f4047242ff544e8c28ea",
  "34217b3662a270aebd32ba92959c7963f1a0a3df138995deeb82ee659dd56aa1",
  "576c82a40325377e01d4cd9439e10175542bd8bf9c5036b160c4a2de8571543b",
  "239504ba1f186f4ddbf2b5aee9c1dcb3d71ca2a6f23f9c6065fd48ba4d9515c5",
  "0676523fc5fdba52571dfbdc1915d9fbb37e3348807f22ff9bf655ef96c1c0b8",
  "7137292ea7519ed12e6c2c4a1a95991d8038f24e9dccae407d8946fdf08cffa0",
  "557b1edebb4016e06b85cffb4cdd2c3c570b99ab6c348bd3850860f27511e6ea",
  "7a84a5182d7823a62692c28a9e5f63e54d34f6ff5b511d491a498f0a05dc7212",
  "5bd11dc06023832a24824ac3e936cca281ac2984cd0187d9ac1f6aa30114f56e",
  "3112d666d6426a2bcf2801fabf201fd76ce0d2ee0695517963a1c4fdd2f4fdf5",
  "0ec15b8cffb1c56da4bf14571f1ea56e17373ddf3d6fe243750f623aacd511cd",
  "4422f34a26969479f535bf8c5eb48da1de03b1ea1d0301824e7b08b4f5eee769",
  "3ba47b66bafb9ee65eca6cc6fa882dcec23124c0b6208536e2437882e58f36ca",
  "6340ec0e77b3e4c70a73f74acc279915c23a0ccdb8afab8cc493036c364c2489",
  "202a8441408cb746d1443517c9083e8c1495e0306d3da7b77dd447b61ec10ec6",
  "5d0c27726c1e71bcd059b020c9e6bc14658433c6c2ae7de29757efb41ebb409b",
  "08235ae33d18ab33758d330e10daaa21a9b01643dd58ab05038a21f8cedccbbb",
  "7e2b4147ea720d4b4507e60f2e2d993033167535fd96239d9d4f169c22a3de1f",
  "7b03dc926ea76655f74709594f02f8de8f85fc22b3e7d079413d6b2a3bf04c5b",
  "165d74d1fc4a492806b74eb57bb5370b5cedb73d31e1f833226bd8bf99e0c3fd",
  "6c0aa72f4fe8d321f93ab9ebd55c8b1982c8394e48bf6939dc5acafb5d781f1b",
  "64634d96811bec3193a19faf7590527bc768d6fe32ff50beb5f6c41455f363ea",
  "4b07e10eeb64dc4d028101ce25f0de763a2a389f66cd35509c9b2ea7648c187b",
  "00ccbcee1d166945295159b022860a3a755be968642ca6d9acc06418cd2783e2",
  "170dfa2d4ade9dc975ee43ea29fd327cdb8970d73972c44052e60dc0c738c5f1",
  "3f62a36fae20df5be590bb457f14151803f8c3b4601dfc057eb8d429bbbac344",
  "6da13fb10700a471954a76f81a9cb89c1703fd47ee9d0d3b09b7729ea044fe97",
  "6b80f1150ad704bbd974aeb07968ceafee822776f43aba413f3a94b132e2d47e",
  "3176c1a1c14dfff4c635e377f4a580d2bf2fbcf0be860dea96d8c5b0b21d3449",
  "555838957e5fcdb45cf0dfa7f1f08252eb08093c837e7ae36608c116c2ae1652",
  "605e8aab144f881cf71ef1a928c3d3312dce7c7a8971674cc157dc8cc652e4bb",
  "55babe5d489b5561fe87d510a456ad881d3b727e3cb357bf5da4eb4d09c00b24",
  "1660dc5883d095bee0de412b06e5b39a879546610ea8335a91cbd5ebc3d295db",
  "059023d7c5e5330bb55e0bdfe0591571f00a1974fb472da626195daab982b804",
  "4c0ec9548e370de9ff72812dcfce7ef729857a484c74b14fe86c9cc6db4c6268",
  "77e832493393af50d84b4ad3a7a50b4aa744e95db65e99f78fe0cdba03f36c6d",
  "3231837db0283e2652ffdaebf80aa62c8acfe6bfe2e474cd95a56598629506fc",
  "2864ebfe07f06c4f566cd7e04356126350523c7ba87f157320a7cb9608aba98a",
  "66db1f9826d3a42b314da4c6848659bc9806ae57cbca61f9632319e7b172a02f",
  "5b693d0127407a26710340d885a87c2bde33bf4f8dd92e3b8da434c697c828b9",
  "6028f178de46f545d707de2e93052870153c7ee9437e2c27300ed146eec59626",
  "1b4849ffe0a051dac38241da79c7dfab744388254a354516c2c969f552aba47c",
  "73d631ad1ccabe137385d8e193038c2cad6916f6aaf27781a70f139d142dfca5",
  "6062db95922b73ac9048773e724381b998e5429c72f79ab89e89e8078daa7268",
  "481660f972947ae1c248121274c776ef17ae26defffc216a36286067671cf833",
  "2b868c8a43da19eb4519c6259c277045842d99521e8fe6154d4fbf53607621a0",
  "3508b478bb7b5795911c6db80736eac15b0ace162d788cf0a8731b6b4cb856a2",
  "0c3f843b6f82e6c7b2c0e9b658ec20e8a7ea2602a0adde45318bb2996b03a3fc",
  "400f96724adb6966852f006daf61844a178f9bca64d6c41f54488fefcd2b640b",
  "512ccf07e3852f36b2476aef234e26eac7b7a424cddf29b79ae6e946d7788f15",
  "40f0a20732cf0e7a482c5882e4ac4750bf88c670a2a50e764f4111d86d1cba2a",
  "6679c6e00708c961c9a028bd2d33218c5faa0cb7c314e899fdbc7b5567968279",
  "3a4bece2d972aadd8fb2ef513c42b5b5010e440e51304cb333bb6ded232791e9",
  "1f96684e43bbf33877d5abd55d0bc9c4528f35b945bc600d64d404b9cc564dad",
  "257ef97d452950b855a2c961ba13da18d97b8cfff634705e338a8f2e2022a1ee",
  "157b8c1e114f8dc75c7d212bb2212fd4a44b6bed17d087041e3978ba1c3a3e0f",
  "4920a1a4536125cf98b8cac6221938b9ac677053a0f31e2f491d3e40238dfc80",
  "1b2a0d9ec48dc055dbe8a3185e7189cde2c56c0aca697a16788ab76dac382525",
  "6e2adf4420cdb4e81e9b7517658fd2bb5df2096d9731f53570f8307d89c45276",
  "71221796f8719b0c5e8059ca030624406ec78b150f17bfc116c706c46939d702",
  "2d8b17d253d1c962291dad1933d50c08e9a51eb82248a1b575158429969c3a32",
  "02b25ce20d302f9d6cf8dfb8500e4acb55f75907a2172d05beb909ac8faee54c",
  "23a164c14700d1a6dd06740b60c5bfb7a8905c2cc811c717fec37b50fd11a736",
  "2dc7266249a36140a459451d0d7ca20cb63716f33efcbe45d5897a589fd1bd55",
  "4e363e1d75eca00e7c3f9c16bd18e8f8dd45c486329e5be168e71fb7388ad3a6",
  "29d9d4d1e76bab67d8419d0e2799fa5c12203941ed672cce1e305509c2e130bd",
  "3c24bdeeeff2c903c7f9ecaa9c7a5e1d60b959baa6193750af3d0b580594e660",
  "13922ac8a2361e79a2542b51aaef63229f95465a6962622a573648f30783908c",
  "2d2258563fb8942937758f6e16e900a3b22afcf79afef5508e3458619ed6ca20",
  "0e0ee78ebfc1368ca9abc00ab3aa373c968193154fb751e7a2aab832aa295003",
  "1a922d36f58f7f40d3d3ebcd6b230d7bad95987e7c73b333d50443ff1c20e5a2",
  "0f3d083f4d33c07c462304b7b30b3c3a1586e1f3586b96947f86d199f183670b"
]
```



**3. 查询余额与更多钱包命令:**

现在你可以查询钱包的余额，并使用其他命令来管理钱包。

```bash
# 在容器内执行

# 查询总余额
bitcoin-cli -regtest -rpcuser=user -rpcpassword=pass -rpcwallet="" getbalance

50.00000000

# 列出所有未花费的交易输出 (UTXO)
bitcoin-cli -regtest -rpcuser=user -rpcpassword=pass -rpcwallet="" listunspent

[
  {
    "txid": "6cb0730ddaccdad690c8d4c7e53661087874e4329ebdc25bc2019e21643c32da",
    "vout": 0,
    "address": "bcrt1qjyay7yw3jry27et53k8turpcrxdzrueq4m758d",
    "label": "",
    "scriptPubKey": "0014913a4f11d190c8af65748d8ebe0c38199a21f320",
    "amount": 50.00000000,
    "confirmations": 101,
    "spendable": true,
    "solvable": true,
    "desc": "wpkh([5439446a/0'/0'/0']0344b497a4f2bb86d10fe63c383d7cd2fcde8289f8b11ed29c5cdf5e963a71026d)#99lmhj2m",
    "safe": true
  }
]

# 获取一个新的收款地址
bitcoin-cli -regtest -rpcuser=user -rpcpassword=pass -rpcwallet="" getnewaddress "my_label"

bcrt1qutxxjxclaen6rtw4xkwnjtehhmermjt6kru553


# 列出最近的10笔交易
bitcoin-cli -regtest -rpcuser=user -rpcpassword=pass -rpcwallet="" listtransactions "*" 10

[
  ..............
  {
    "address": "bcrt1qjyay7yw3jry27et53k8turpcrxdzrueq4m758d",
    "category": "immature",
    "amount": 50.00000000,
    "label": "",
    "vout": 0,
    "confirmations": 1,
    "generated": true,
    "blockhash": "0f3d083f4d33c07c462304b7b30b3c3a1586e1f3586b96947f86d199f183670b",
    "blockheight": 101,
    "blockindex": 0,
    "blocktime": 1762670932,
    "txid": "c936555d28a35396064c8fd617f1c5f8386b01f77833951588ac15b295e19aba",
    "walletconflicts": [
    ],
    "time": 1762670914,
    "timereceived": 1762670914,
    "bip125-replaceable": "no"
  }
]

```

**4. 发送交易（转账）:**

从节点钱包向另一个地址发送比特币。

```bash
# 在容器内执行

# 首先，创建一个接收地址
recipient_address=$(bitcoin-cli -regtest -rpcuser=user -rpcpassword=pass -rpcwallet="" getnewaddress "recipient")

root@213f6bb854da:/# echo $recipient_address
bcrt1qf0xtxyjwgknw96uw2mhcsj9rspek0rlze6lfsf

# 发送 10 BTC 到该地址
txid=$(bitcoin-cli -regtest -rpcuser=user -rpcpassword=pass -rpcwallet="" sendtoaddress $recipient_address 10)


root@aac1e3e855d3:/# echo "Transaction ID: $txid"
f1de3f44be6abe128cb3cfc4850390a83722e7b43d86e910f45eefd8e8010b6b


# 交易发送后，它会进入内存池 (mempool)。为了让交易被确认，你需要挖一个新的区块。
bitcoin-cli -regtest -rpcuser=user -rpcpassword=pass -rpcwallet="" generatetoaddress 1 $address

[
  "61f52b88c5de4870323755de552e5447cb2dc5ebdf930c89cf01003b7fb5201d"
]
```

**5. 查询交易详情:**

使用上一步得到的 `txid` 来查询交易的详细信息。

```bash
# 在容器内执行 (将 <txid> 替换为真实的交易ID)
bitcoin-cli -regtest -rpcuser=user -rpcpassword=pass -rpcwallet="" gettransaction <txid>


root@aac1e3e855d3:/# bitcoin-cli -regtest -rpcuser=user -rpcpassword=pass -rpcwallet="" gettransaction f1de3f44be6abe128cb3cfc4850390a83722e7b43d86e910f45eefd8e8010b6b
{
  "amount": 0.00000000,
  "fee": -0.00000141,
  "confirmations": 1,
  "blockhash": "61f52b88c5de4870323755de552e5447cb2dc5ebdf930c89cf01003b7fb5201d",
  "blockheight": 102,
  "blockindex": 1,
  "blocktime": 1762671597,
  "txid": "f1de3f44be6abe128cb3cfc4850390a83722e7b43d86e910f45eefd8e8010b6b",
  "walletconflicts": [
  ],
  "time": 1762671541,
  "timereceived": 1762671541,
  "bip125-replaceable": "no",
  "details": [
    {
      "address": "bcrt1qf0xtxyjwgknw96uw2mhcsj9rspek0rlze6lfsf",
      "category": "send",
      "amount": -10.00000000,
      "label": "recipient",
      "vout": 1,
      "fee": -0.00000141,
      "abandoned": false
    },
    {
      "address": "bcrt1qf0xtxyjwgknw96uw2mhcsj9rspek0rlze6lfsf",
      "category": "receive",
      "amount": 10.00000000,
      "label": "recipient",
      "vout": 1
    }
  ],
  "hex": "0200000000010175462b2bfa5f3a98158168cb6dbdd549277e9c0d6a886bf6cb50385ec90a17df0000000000feffffff0273276bee00000000160014b378be997cd1a736153b9577061c5179a8c1fb6a00ca9a3b000000001600144bccb3124e45a6e2eb8e56ef8848a38073678fe20247304402206490ccaafe31920283cf4401fc5deafdcd104c3ec748ffb46f9e824ac8a8613f022037ed12c68957de637ea38a0bdb140612ddf1e8fff3bbb103fa58cfc1105a6e530121029603c5ce7a340a4d5539b3da774fd30d1577731bc844b75504280aced3a388d365000000"
}

```

#### 4.5 使用项目 API 进行测试

当 `bitcoin` 服务运行后，你可以使用 `curl` 或 Postman 等工具调用 `WalletController` 提供的 API 接口进行测试。服务默认运行在 `7001` 端口。

**1. 获取区块高度:**

```bash
curl http://localhost:7001/rpc/height
```
这应该会返回一个 JSON 对象，其中 `data` 字段的值为 `101`（因为我们挖了 101 个块）。

**2. 为用户创建新地址:**

为名为 `testuser` 的账户创建一个新的比特币地址。

```bash
curl http://localhost:7001/rpc/address/testuser
```
API 会返回一个新的比特币地址，并将其与 `testuser` 关联存储在数据库中。

**3. 从节点钱包向新地址转账:**

现在，我们模拟一次充值，从 `bitcoind` 节点的钱包向刚刚为 `testuser` 生成的地址转账。

```bash
# 首先，获取 testuser 的地址（替换为上一步生成的地址）
USER_ADDRESS="<replace-with-generated-address>"

# 在容器内执行转账命令，发送 1.5 BTC
docker exec bitcoin-regtest bitcoin-cli -regtest -rpcuser=user -rpcpassword=pass -rpcwallet="" sendtoaddress $USER_ADDRESS 1.5

# 为了让交易被确认，需要再挖一个新区块
docker exec bitcoin-regtest bitcoin-cli -regtest -rpcuser=user -rpcpassword=pass -rpcwallet="" generatetoaddress 1 $(docker exec bitcoin-regtest bitcoin-cli -regtest -rpcuser=user -rpcpassword=pass -rpcwallet="" getnewaddress)
```

交易确认后，`BitcoinWatcher` 组件会自动检测到这笔充值，并将其记录到数据库中。

**4. 查询钱包总余额:**

查询 `bitcoind` 节点钱包的总余额。

```bash
curl http://localhost:7001/rpc/balance
```

**5. 查询特定地址的账户余额:**

查询与 `testuser` 关联的地址的余额。

```bash
# 替换为 testuser 的地址
USER_ADDRESS="<replace-with-generated-address>"
curl http://localhost:7001/rpc/balance/$USER_ADDRESS
```

**6. 从项目钱包提现（转账）:**

测试从 `bitcoin` 服务控制的钱包向外部地址转账。

```bash
# 在容器内生成一个接收地址
RECEIVE_ADDRESS=$(docker exec bitcoin-regtest bitcoin-cli -regtest -rpcuser=user -rpcpassword=pass -rpcwallet="" getnewaddress)

# 调用提现接口，发送 0.2 BTC
curl "http://localhost:7001/rpc/transfer?address=$RECEIVE_ADDRESS&amount=0.2&fee=0.001"
```
接口会返回交易的 `txid`。同样，你需要挖一个新块来确认这笔交易。

---

### 5. 常见问题 (FAQ)

**Q1: 一个区块能存储多少笔交易？**

A: 这不是一个固定的数字，它取决于区块的大小限制（约1MB）和每笔交易的实际大小。一笔简单的交易约250字节，据此估算，一个区块大约可以容纳2000到4000笔交易。

**Q2: 通过 `WalletController` 的接口发起转账后，会自动生成区块来确认交易吗？**

A: **不会。** `WalletController` 的转账接口只是将交易广播到比特币网络，使其进入节点的内存池（Mempool）等待确认。它本身不会触发挖矿或区块生成。在 `regtest` 测试模式下，你必须手动执行 `generatetoaddress` 命令来挖出一个新区块，从而使交易得到确认。