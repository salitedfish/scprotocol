# scprotocol

网络协议接入组件（协议库），包名 `com.labdatahub.protocol`。不是独立应用，编译为 jar 后被 `lab-data-hub-api`（`protocol/` 目录）依赖使用，负责各网络协议的编解码与消息处理。**协议库只做纯编解码，不含网络 I/O、不感知设备地址与连接**。

> 本文件只描述**当前状态**；改动过程与历史排查记录见仓库根 `开发日志/`。

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
│              #   FanucFocasProtocol（FANUC 0i 数控，FOCAS2 只读）、
│              #   MitsubishiMc3eTcpProtocol（三菱 PLC，MC QnA 兼容 3E 二进制帧，TCP 5007，读写）、
│              #   MitsubishiMc1eTcpProtocol / MitsubishiA1eTcpProtocol（三菱 PLC 另两种帧，骨架）、
│              #   MitsubishiCncTcpProtocol（三菱 CNC MELDAS MOCHA，TCP 683 只读）
├── deal/      # 协议具体实现/处理：MqttClientDeal、MqttServerDeal、TcpServerDeal、UdpServerDeal、
│              #   CoapServerDeal、HttpServerDeal、WsServerDeal、ModbusTcpDeal、S71200TcpDeal、
│              #   OmronFinsTcpDeal、DataBaseTcpDeal、BrotherTcpDeal、FanucFocasDeal、
│              #   MitsubishiMc3eTcpDeal、MitsubishiMc1eTcpDeal（骨架）、MitsubishiA1eTcpDeal（骨架）、
│              #   MitsubishiCncTcpDeal
├── message/   # 消息模型：DecodeMessage、EncodeMessage、DeviceRegister、RangeItem
└── utils/     # RedisContextHolder、RegisterValueUtils 等工具
```

`RegisterValueUtils`：寄存器值按物模型解析参数转换，`convert(List<Integer>, dataType, byteOrder, isSigned, scale, offset)` 支持 int/float/double/bool/string，`applyScale` 只做缩放偏移（S7 用）。边界规则：int 解析 3 个字（48 位）时按 isSigned 做 48→64 位符号扩展、4 个字（64 位）原样返回；float/double 只接受 2 字/4 字，1/3 字返回 null。Modbus/S7/Fins/Brother/Mitsubishi 五个轮询协议的 `decode` 都依据后端传来的物模型解析参数解析。其中 `BrotherTcpDeal.decode` 收到的 `value` 是响应行按点位地址（`数据区.行号.字段序号`）取出的原始字符串，按 dataType 转类型后套 `applyScale`，byteOrder/isSigned 不适用（ASCII）。

## 三菱 MC：按帧型拆成三个独立协议

三种帧各自是一个 netType、各自单独编译上传、各自注册 mainClass，互不依赖、不复用彼此的软元件码表与命令常量（目的是能各自独立演化）。差异只在**帧头与寻址**，协议库这一层（值 ↔ 类型转换）帧无关，所以三份 decode 几乎一样。

| netType | 帧型 | 适用机型 | 状态 |
|---|---|---|---|
| `MITSUBISHI_MC3E_TCP` | QnA 兼容 3E 帧（21 字节请求） | Q / QnA / iQ-R / iQ-F | **已实装并验收**（读写皆通） |
| `MITSUBISHI_MC1E_TCP` | QnA 系列 1E 帧（19 字节请求） | QnA 系列老机型 | 协议库本层已就绪，**api 侧帧层与平台接线未做** |
| `MITSUBISHI_A1E_TCP` | A 兼容 1E 帧 | A 系列（AnA/AnU/AnS） | **仅建立协议文件**；`encode` 显式抛 `UnsupportedOperationException` |

### `MITSUBISHI_MC3E_TCP`（`MitsubishiMc3eTcpProtocol` / `MitsubishiMc3eTcpDeal`）

- **decode** 与 FINS 同构：后端 `MitsubishiMc3eDataReader`（api 组件包 `mitsubishi_mc3e_tcp`）按 MC 3E 二进制帧（小端，21 字节请求）读出原始数值（字读每点 2 字节小端整数 / 位读 0/1）塞进 `jsonArray`（`List<Integer>`）；decode 收到 `code/deviceSn/dataType/byteOrder/isSigned/scale/offset` + 扁平 `jsonArray`（一个 code 对应一个点/块），用 `RegisterValueUtils.convert` 转类型。
- **写值 encode**：收到扁平 `otherConfig`（9 个键，`KEY_*` 常量：`dataType`/`byteOrder`/`isSigned`/`scale`/`offset`/`value`/`address`/`count`/`areaCode`），产出**单个 JSON 对象**载荷 `{areaCode,startAddress,count,words[],dataType}`（5 个键，`OUT_*` 常量），塞进 `EncodeMessage.modbusWriteJson` 返回（`isSend=true`）。
  - `startAddress` / `words` 与 FINS **刻意同名**：载荷形态本来一样，这样 api 侧 `ProtocolEncodeInvoker#parseWriteEcho` 的形态判别（含 `"words"` → `parseWordsEcho`）不必为三菱再分一支。**回传槽仍是 `EncodeMessage.modbusWriteJson`** —— 与 S7/FINS 同样的历史包袱，改名要协议 jar 与 api 同时发版。
  - **字设备 / 位设备分叉**（严格镜像 api 侧 `MitsubishiMc3eDataReader` 的读法）：**字设备**（D `0xA8` / W `0xB4` / R `0xAF` / ZR `0xB0` / SD `0xA9`）复用 `RegisterValueUtils.encode`，值与 `count` 共同决定编码（count=1→16 位整型、2→int32/float32、4→int64/double64，与读侧 `convert` 对称）；**位设备**（M `0x90` / L `0x92` / B `0xA0` / X `0x9C` / Y `0x9D` / S `0x98` / SM `0x91` / F `0x93`）只接受布尔值、产出 `[0]`/`[1]`，且 `count` 必须为 1。
  - **本侧比 api 侧更严**：两张码表都不认的软元件码**直接拒**（api 读侧把「非字设备」一律当位设备，读方向无所谓，**写方向会静默写错地方**）。上限用本侧 `MAX_WORD_COUNT=960` / `MAX_BIT_COUNT=2000`，不复用 `RegisterValueUtils.encode` 内部的 123（那是 Modbus 功能码 16 的规范值，会把合法 MC 字写挡掉）。
  - ⚠️ **`byteOrder` 在 MC 里是有意义的**：MC 帧内多字节字段是小端，api 侧读出来是宿主序 16bit 字列表，所以它只决定**多字值的字序**，与读侧 `convert` 同一判据，读得出来就写得进去。取值 `big`/`little`（也认 `大端`/`小端`/`BE`/`LE`），**空默认大端**（`RegisterValueUtils.isBigEndian`），物模型列 `byte_order` 库默认也是 `'big'` —— **三菱 MC 的多字点位（int32 / float32 / double64，即 `length≥2`）必须显式选「小端」**，否则跨字值的字序会反。单字点位不受影响。
  - ⚠️ **软元件码表在本包内已有两份**（`MitsubishiMc3eTcpDeal`、`MitsubishiMc1eTcpDeal`），api 侧 `MitsubishiMc3eDataReader` 还有一份 —— 协议库与 api 是两个编译单元共享不了，三菱这几种帧在协议库内也没做公共父类。判错不报错、只是写错地方，改一处必须同步其余几处。
  - 编码异常 `IllegalArgumentException` 经 api 侧 `ProtocolEncodeInvoker` 反射还原为 **422**。

### `MITSUBISHI_MC1E_TCP`（`MitsubishiMc1eTcpProtocol` / `MitsubishiMc1eTcpDeal`，骨架）

协议库本层已就绪：decode/encode 与 3E 同源 —— 1E 帧与 3E 帧**共用同一套命令**（批量读 `0x0401` / 批量写 `0x1401`，子命令 `0x0000` 字 / `0x0001` 位）**和同一张软元件码表**，差异只在帧头。**缺的是 api 侧帧层**：`labdatahub-component/.../mitsubishi_mc1e_tcp` 组件包不存在（组帧与网络 I/O 都在那层）⇒ 当前读不出数据。平台接线也一处没加（`NetType` / `MAIN_CLASS_PATH` / component open-close / `TimerTask` 起调度 / 建表 / 前端下拉与详情 tab / `PointWriteMetaResolver` 准入 / `PointWriter` 分派），所以它目前既上传不了也选不到，不会以「能选但连不上」的形态误导人。真机到位后按类注释里的顺序补全。⚠️ **组帧最易错的一处：1E 帧起始地址 2 字节，3E 是 3 字节。**

### `MITSUBISHI_A1E_TCP`（`MitsubishiA1eTcpProtocol` / `MitsubishiA1eTcpDeal`，骨架）

decode 可用（帧无关的值转换），**`encode` 恒抛 `UnsupportedOperationException`** —— A 兼容 1E 帧的软元件码表与命令集和 QnA 系不是一套，照 QnA 码表写会**静默写错软元件**，在确认 A 系帧格式前一律拒绝写入。**刻意不用 `isSend=false` 的只读 stub**：那种写法在本平台语义里等于「写成功」，是谎报。

## 三菱 CNC（`MitsubishiCncTcpProtocol` / `MitsubishiCncTcpDeal`，`MITSUBISHI_CNC_TCP`）

decode 与 FANUC 单值模式同构（参照树根 `MitsubishiNcTcpPacket.dll` 逆向实现，**待真机验证**）：后端 `MitsubishiCncDataReader`（api 组件包 `mitsubishi_cnc_tcp`）按 MOCHA 帧（GIOP 风格，TCP 683）一次请求一个点位（`readType[.axisNo]`，轴点 axisNo 1-6 = X/Y/Z/A/B/C），按响应 DataType 解析出**单个原始字符串**塞进 `value`；decode 收到 `code/deviceSn/value/dataType`，私有 `convertValue` 按 dataType（bool/int/long/float/double/string）转类型 + `RegisterValueUtils.applyScale`（byteOrder/isSigned 不适用），结果 `properties.put(code, converted)`。encode 为只读 stub（`isSend=false`）。

## Modbus / S7 decode 要点

- **Modbus 收到的是合并后的单个连续块**：调度把 `registerRange` 的多个子区间合并为 `[minStart, maxEnd]` 一块读取，decode 的 jsonArray 只有一个 item。`applySigned`/`applyScale` 按物模型解析参数套用；bool 按字非零转 0/1。
- **S7 的 `applySigned` 只对 DBW（16 位整型）生效**：`S71200TcpDeal.decode` 收到 `blockType`，仅 `blockType == null || "DBW"` 时套用 —— `applySigned` 做 16 位符号扩展，对 DBD 的 DINT 32 位整型（低 16 位 >0x7FFF 时）会截断成负数；DBD/DBB 直接 `applyScale`。DBD 的 int 系列解码在 api 侧 `S7DataReader` 完成，decode 收到的已是原始值。

## 写值 encode

### S7（`S71200_TCP`）

`S71200TcpDeal.encode` 收到扁平 `otherConfig`（12 个键，`KEY_*`：`dataType`/`byteOrder`/`isSigned`/`scale`/`offset`/`value`/`address`/`dbNumber`/`areaType`/`blockType`/`bitOffset`/`length`），按块类型编码成字节后组**单个 JSON 对象**载荷 `{areaType, dbNumber, address, bitOffset, blockType, dataType, bytes[]}`（7 个键，`OUT_*`），塞进 `EncodeMessage.modbusWriteJson`（`isSend=true`）。

- ⚠️ **三处跨 classloader 字符串契约**（api 与协议 jar 无编译期检查，改一边必须同步另一边）：① 入向 `OpenApiEncodeKeys.*` ↔ 本侧 `KEY_*`；② 出向 `OUT_*` ↔ api 侧 `S7PointWriter.KEY_*`；③ 回传槽仍是 `EncodeMessage.modbusWriteJson`。**Map value 只能放 JDK 标量**。
- ⚠️ **fastjson2 默认把 `byte[]` 序列化成 base64**（`WriteByteArrayAsBase64`）→ 载荷里的 `bytes` 必须显式转 `List<Integer>`（0-255 无符号，`toUnsignedList(byte[])`），否则 api 侧认不出。
- **字节编码矩阵（严格镜像 api 侧 `S7DataReader` 的读法，读得出来就写得进去）**：`DBW` 恒 2 字节 16 位整型（**无视 dataType**）；`DBD` 4 字节，`isIntType(dataType)`（int/integer/short/long/byte）→ DINT int32，否则 Real float32；`DBX` 恒位（1 字节 0/1 + `bitOffset` 0-7，越界抛 `IllegalArgumentException`）；`DBB` 恒西门子 STRING（`[0]`=最大长度=length-2、`[1]`=实际长度、内容从 index 2 起、**GBK** 编码 —— 读侧就是 GBK 解码，只拿英文测永远发现不了）。全部大端。不支持/缺块类型直接抛异常。
- `RegisterValueUtils.encodeBytes(value, dataType, byteOrder, isSigned, scale, offset, byteCount)` 是写方向数值编码入口：反缩放（`scale=0` 拒绝）→ 按 2/4 字节查 `isIntType` 定整型或浮点 → `integerToBytes`。`parseBoolean` 提为 public 供 DBX 复用。**`isIntType` 在 api 侧 `S7DataReader` 有一份等价实现，两处是同一判据的两份代码。**
- 编码异常经 `ProtocolEncodeInvoker` 反射还原：`IllegalArgumentException` → **422**，其余 → 500。
- ⚠️ **本侧只产字节，不做读-改-写**：DBX 的「先读原字节再置/清位」由 api 侧 `S7PointWriter.writeBit` 完成。`bitOffset` 原样放进载荷由 api 侧消费。

### FINS（`OMRONFINS_TCP`）

`OmronFinsTcpDeal.encode` 收到扁平 `otherConfig`（10 个键，`KEY_*`：`dataType`/`byteOrder`/`isSigned`/`scale`/`offset`/`value`/`address`/`count`/`areaCode`/`bitAddress`），产出**单个 JSON 对象**载荷 `{areaCode, startAddress, bitAddress, count, words[], dataType}`（6 个键，`OUT_*`），塞进 `EncodeMessage.modbusWriteJson`（`isSend=true`）。**载荷形态与 Modbus 的 `RangeItem` 数组不同** —— Modbus 一条载荷可覆盖多个区间，FINS 一条就是一次内存区写命令。

- **字区/位区分叉**（严格镜像 api 侧 `FinsDataReader`）：**字区**复用 `RegisterValueUtils.encode`（count=1→16 位整型、2→float32/int32、4→double64/int64，与读侧 `convert` 对称）；**位区**只接受布尔值（`parseBoolean`），产出 `[0]`/`[1]`，`count` 必须为 1。字区载荷不带 `bitAddress` 键（fastjson2 默认不输出 null）。
- **上限用本侧 `MAX_ITEMS = 1000`**，不复用 `RegisterValueUtils.encode` 内部的 123（Modbus 功能码 16 的规范值，会挡掉合法点位且错误文案提到不相干的功能码）。
- **本侧只产字值/位值，不做读-改-写**：FINS 有原生位写命令（区码换成位区码 + 地址第 3 字节填位号），一次调用完成，没有 S7 那种「对 PLC 用户程序不原子」的窗口。地址/位号范围校验在 api 侧 `FinsPointWriter.parsePayload`。
- ⚠️ **`isBitArea` 是同一判据的四份实现**：本侧 `OmronFinsTcpDeal.isBitArea`、api 侧 `FinsDataReader.isBitArea`、controller `checkConfig`、前端 `OmronFinsTcpConfig.vue` 的 `BIT_AREA_ITEMS`。**改一处必须同步其余三处** —— 判错不报错，而是把位当字读/写，静默读写到另一个区。
- **标准区码成对出现**（字访问码 / 位访问码，依据 W342 内存区指定表）：CIO `0xB0`/`0x30`、WR `0xB1`/`0x31`、HR(H) `0xB2`/`0x32`、AR(A) `0xB3`/`0x33`、DM `0x82`/`0x02`、EM 当前库 `0x98`/`0x18`、EM 库 0-15 `0xA0-0xAF`/`0x20-0x2F`。**不存在 IR 区**（CS/CJ 没有 IR；`0x88` 是 CNT 计数器区）。
- 编码异常 `IllegalArgumentException` 经 api 侧 `ProtocolEncodeInvoker` 反射还原为 **422**。

### 写值能力一览

| 协议 | encode 行为 |
|---|---|
| `MODBUS_TCP` / `S71200_TCP` / `OMRONFINS_TCP` / `MITSUBISHI_MC3E_TCP` | **已实装**，产出载荷、`isSend=true` |
| Brother / FANUC / MitsubishiCnc / HTTP_SERVER | **只读 stub / 空壳**，返回 `isSend=false` |
| `MITSUBISHI_A1E_TCP` | **未实现**，恒抛 `UnsupportedOperationException`（不是 stub —— stub 在本平台语义里等于「写成功」，那是谎报） |

api 侧 `PointWriteMetaResolver` 第 4 步按 netType 准入，**非上面四个已实装协议一律 409**，不会走到 stub 与 A1E 的抛错分支。那两条路径属于「将来接通时的正确失败方式」。

## 只读协议

- **Brother**：`PDSP` 等语义点位表（语义点位 → 地址映射）是前端/后端配置层职责，协议库不感知。前端 `lab-data-hub-web/src/utils/brotherTcpPoints.js` 内置点表，配置 code 填语义 key；本库只按后端传入的 `code/value/dataType` 解析，地址约定是 `数据区.行号.字段序号`。
- **FANUC FOCAS2**（`FanucFocasProtocol` / `FanucFocasDeal`，`FANUC_TCP`）：decode 按 `code/deviceSn/value/dataType` 解析，地址 = `采集项类型.参数1.参数2`（如 `axis.1.1` = X 轴机械坐标）。**不感知 FOCAS2 地址、不含网络 I/O/JNA**；实际采集（fwlib JNA 绑定、连接、调度、自愈）全在 api 的 `com.labdatahub.component.fanuc_focas`，decode 收到的是 `FanucFocasDataReader` 读出的原始字符串，按 dataType 转类型 + `applyScale`。
- **HTTP_SERVER**（`HttpServerProtocol` / `HttpServerDeal`）是接入侧协议：外部系统主动 POST 进来，平台起内嵌 Tomcat 把**原始请求整包**（`requestMethod/requestPath/contentType/headers/queryParams/requestBody/formData`）交给 `decode`，**接口形状完全由这个 deal 定义**。出厂实现 = `POST /<任意路径>` + 扁平 JSON，`deviceSn` 从 body 顶层读，**body 每个 key（含 `deviceSn` 自身）都进 `properties`**；`httpNeedReply=false`（实际响应被平台置空），`encode` 是空壳（走 api 侧 409 口径）。改了这里的 `decode` 要重编 jar 并在前端「协议管理」**重新上传注册**；`decode` 返回 null 时平台直接丢弃该请求（连 `device.up` 都不发）。平台侧用法（端口/needReply/规则引擎/物模型/心跳）见 `lab-data-hub-api/CLAUDE.md`「HTTP_SERVER 接入侧协议」。

## 构建

```bash
mvn clean install -DskipTests
```

产出 jar 后按 `lab-data-hub-api/README.md` 放入其 `protocol/` 目录；**真正生效要走前端「协议管理」上传注册**（见根 `CLAUDE.md`「协议 jar 部署」）。

## 编码规范

1. **所有 class 字段必须加中文注释**。
2. 列表循环尽量用增强 for，少用下标循环。
3. 新增协议遵循现有 `parent/` + `deal/` 两段式结构：先在 `parent` 定义协议基类，再在 `deal` 实现具体处理。
