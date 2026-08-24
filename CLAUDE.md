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
│              #   BrotherTcpProtocol（Brother NC 数控，TCP 10000 只读）、
│              #   FanucFocasProtocol（FANUC 0i 数控，FOCAS2 只读）
├── deal/      # 协议具体实现/处理：MqttClientDeal、MqttServerDeal、TcpServerDeal、UdpServerDeal、
│              #   CoapServerDeal、HttpServerDeal、WsServerDeal、ModbusTcpDeal、S71200TcpDeal、
│              #   OmronFinsTcpDeal、DataBaseTcpDeal、BrotherTcpDeal、FanucFocasDeal
├── message/   # 消息模型：DecodeMessage、EncodeMessage、DeviceRegister、RangeItem
└── utils/     # RedisContextHolder、RegisterValueUtils 等工具
```

`RegisterValueUtils`：寄存器值按物模型解析参数转换工具，`convert(List<Integer>, dataType, byteOrder, isSigned, scale, offset)` 支持 int/float/double/bool/string，`applyScale` 仅做缩放偏移（S7 用）。Modbus/S7/Fins/Brother 四个轮询协议的 `decode` 依据后端传来的物模型解析参数解析；其中 `BrotherTcpDeal.decode` 收到的 `value` 是响应行按点位地址（数据区.行号.字段序号）取出的原始字符串，按 dataType 转类型后套 `applyScale`，byteOrder/isSigned 不适用（ASCII）。

- **Modbus decode 收到的是合并后的单个连续块**：`ModbusMessageConsumeService` 把 `registerRange` 的多个子区间合并为 `[minStart, maxEnd]` 一块读取，decode 的 jsonArray 只有一个 item（code 键不再被多区间互覆盖）。`applySigned`/`applyScale` 按物模型解析参数套用；bool 类型按字非零转 0/1。
- **S7 decode 的 applySigned 只对 DBW（16 位整型）生效**：`S71200TcpDeal.decode` 收到 `blockType`，仅 `blockType == null || "DBW"` 时套 `applySigned`——因为 `applySigned` 做 16 位符号扩展，对 DBD 的 DINT 32 位整型（低 16 位 >0x7FFF 时）会截断成负数，DBD/DBB 直接 `applyScale`。DBD 的 int 系列解码（手动 4 字节大端 int32 / float32）在 `lab-data-hub-api` 的 `S7DataReader` 侧完成，decode 收到的已是原始值。

Brother 的 **PDSP 语义点位表（语义点位 → 地址映射）为前端/后端配置层职责，协议库不感知**：前端 `lab-data-hub-web/src/utils/brotherTcpPoints.js` 内置点表，配置 code 填点位语义 key；本库只按后端传入的 `code/value/dataType` 解析，地址约定仍是 `数据区.行号.字段序号`，无协议库改动。

**FANUC FOCAS2（`FanucFocasProtocol` / `FanucFocasDeal`，netType/protocolType = `FANUC_TCP`）**：协议库仍只做解码（decode 按 `code/deviceSn/value/dataType` 解析，地址 = `采集项类型.参数1.参数2`，如 `axis.1.1` = X 轴机械坐标），**不感知 FOCAS2 地址、不含网络 I/O/JNA**。FOCAS2 的实际采集（fwlib32 JNA 绑定、连接、调度、自愈）全部在 `lab-data-hub-api` 的 `labdatahub-component` 模块 `com.labdatahub.component.fanuc_focas` 包，协议库 decode 收到的是该包 `FanucFocasDataReader` 按点位读出的原始字符串，与 Brother 一致按 dataType 转类型 + `applyScale`。

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
