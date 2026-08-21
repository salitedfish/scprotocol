# scprotocol

网络协议接入组件（协议库），包名 `com.labdatahub.protocol`。不是独立应用，编译为 jar 后被 `lab-data-hub-api`（`protocol/` 目录）依赖使用，负责各网络协议的接入、编解码与消息处理。

## 技术栈

- Java 1.8 + Maven
- Spring Boot 2.6.13（依赖管理）
- fastjson2、spring-boot-starter-data-redis、lombok
- 仓库：阿里云 Maven（`pom.xml` 已配置）

## 包结构

```
com.labdatahub.protocol
├── parent/    # 协议基类：MqttClientProtocol、MqttBrokerProtocol、TcpServerProtocol、
│              #   UdpServerProtocol、CoapServerProtocol、HttpServerProtocol、WsServerProtocol、
│              #   ModbusTcpProtocol、S71200TcpProtocol、OmronFinsTcpProtocol、DataBaseTcpProtocol、
│              #   BrotherTcpProtocol（Brother NC 数控，TCP 10000 只读）
├── deal/      # 协议具体实现/处理：MqttClientDeal、MqttServerDeal、TcpServerDeal、UdpServerDeal、
│              #   CoapServerDeal、HttpServerDeal、WsServerDeal、ModbusTcpDeal、S71200TcpDeal、
│              #   OmronFinsTcpDeal、DataBaseTcpDeal、BrotherTcpDeal
├── message/   # 消息模型：DecodeMessage、EncodeMessage、DeviceRegister、RangeItem
└── utils/     # RedisContextHolder、RegisterValueUtils 等工具
```

`RegisterValueUtils`：寄存器值按物模型解析参数转换工具，`convert(List<Integer>, dataType, byteOrder, isSigned, scale, offset)` 支持 int/float/double/bool/string，`applyScale` 仅做缩放偏移（S7 用）。Modbus/S7/Fins/Brother 四个轮询协议的 `decode` 依据后端传来的物模型解析参数解析；其中 `BrotherTcpDeal.decode` 收到的 `value` 是响应行按点位地址（数据区.行号.字段序号）取出的原始字符串，按 dataType 转类型后套 `applyScale`，byteOrder/isSigned 不适用（ASCII）。

Brother 的 **PDSP 语义点位表（语义点位 → 地址映射）为前端/后端配置层职责，协议库不感知**：前端 `lab-data-hub-web/src/utils/brotherTcpPoints.js` 内置点表，配置 code 填点位语义 key；本库只按后端传入的 `code/value/dataType` 解析，地址约定仍是 `数据区.行号.字段序号`，无协议库改动。

## 构建

```bash
mvn clean install -DskipTests
```

产出 jar 后，按 `lab-data-hub-api/README.md` 说明放入其 `protocol/` 目录供后端加载。

## 编码规范

1. **AI 修改/创建的 Java 文件**，文件开头添加注释 `//由AI修改`。
2. **所有 class 字段必须加中文注释**。
3. 列表循环尽量用增强 for，少用下标循环。
4. 新增协议遵循现有 `parent/` + `deal/` 两段式结构：先在 `parent` 定义协议基类，再在 `deal` 实现具体处理。
