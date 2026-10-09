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
│              #   FanucFocasProtocol（FANUC 0i 数控，FOCAS2 只读）、
│              #   MitsubishiMc3eTcpProtocol（三菱 PLC，MC QnA 兼容 3E 二进制帧，TCP 5007，读写）、
│              #   MitsubishiMc1eTcpProtocol / MitsubishiA1eTcpProtocol（三菱 PLC 另两种帧，骨架，待真机对接）、
│              #   MitsubishiCncTcpProtocol（三菱 CNC MELDAS MOCHA，TCP 683 只读）
├── deal/      # 协议具体实现/处理：MqttClientDeal、MqttServerDeal、TcpServerDeal、UdpServerDeal、
│              #   CoapServerDeal、HttpServerDeal、WsServerDeal、ModbusTcpDeal、S71200TcpDeal、
│              #   OmronFinsTcpDeal、DataBaseTcpDeal、BrotherTcpDeal、FanucFocasDeal、
│              #   MitsubishiMc3eTcpDeal、MitsubishiMc1eTcpDeal（骨架）、MitsubishiA1eTcpDeal（骨架）、
│              #   MitsubishiCncTcpDeal
├── message/   # 消息模型：DecodeMessage、EncodeMessage、DeviceRegister、RangeItem
└── utils/     # RedisContextHolder、RegisterValueUtils 等工具
```

`RegisterValueUtils`：寄存器值按物模型解析参数转换工具，`convert(List<Integer>, dataType, byteOrder, isSigned, scale, offset)` 支持 int/float/double/bool/string，`applyScale` 仅做缩放偏移（S7 用）。**边界修正**：int 解析 3 个字（48位）时按 isSigned 做 48→64 位符号扩展（避免负数读成正的垃圾大数），4 个字（64位）原样返回；float/double 解析仅接受 2 个字（float32）/4 个字（double64），1/3 个字返回 null 避免凑出垃圾浮点值。Modbus/S7/Fins/Brother/Mitsubishi 五个轮询协议的 `decode` 依据后端传来的物模型解析参数解析；其中 `BrotherTcpDeal.decode` 收到的 `value` 是响应行按点位地址（数据区.行号.字段序号）取出的原始字符串，按 dataType 转类型后套 `applyScale`，byteOrder/isSigned 不适用（ASCII）。

- **三菱 MC —— 按帧型拆成三个独立协议（2026-09-19 拆分，起因见下）**：原本三种帧挤在一个 `MITSUBISHI_MC3E_TCP` 里、用配置列 `protocol_mode`（`'3E'`/`'1E'`）分叉，现在**各自是一个 netType、各自单独编译上传、各自注册 mainClass，互不依赖、不复用彼此的软元件码表与命令常量**（拆开的目的就是让它们将来能各自独立演化）。三种帧的差异只在**帧头与寻址**，协议库这一层（值 ↔ 类型转换）是帧无关的，所以三份 decode 长得几乎一样。

  | netType | 帧型 | 适用机型 | 状态 |
  |---|---|---|---|
  | `MITSUBISHI_MC3E_TCP` | QnA 兼容 3E 帧（21 字节请求） | Q / QnA / iQ-R / iQ-F | **已实装并验收**（读写皆通） |
  | `MITSUBISHI_MC1E_TCP` | QnA 系列 1E 帧（19 字节请求） | QnA 系列老机型 | 协议库本层已就绪，**api 侧帧层与平台接线待真机对接后实现** |
  | `MITSUBISHI_A1E_TCP` | A 兼容 1E 帧 | A 系列（AnA/AnU/AnS） | **仅建立协议文件**；`encode` 显式抛 `UnsupportedOperationException` |

  - **`MITSUBISHI_MC3E_TCP`（`MitsubishiMc3eTcpProtocol` / `MitsubishiMc3eTcpDeal`）**：decode 与 FINS 同构——后端 `MitsubishiMc3eDataReader`（`lab-data-hub-api` 组件包 `mitsubishi_mc3e_tcp`）按 MC 3E 二进制帧（小端，21 字节请求）读出的原始数值（字读每点 2 字节小端整数 / 位读 0/1）塞进 `jsonArray`（`List<Integer>`），decode 收到 `code/deviceSn/dataType/byteOrder/isSigned/scale/offset` + 扁平 `jsonArray` 数组（一个 code 对应一个点/块），`RegisterValueUtils.convert(data, dataType, byteOrder, isSigned, scale, offset)` 转类型解析。协议库**不含网络 I/O、不感知软元件地址**。
  - **`MITSUBISHI_MC3E_TCP` 写值 encode（2026-09-19 实装）**：收到扁平 `otherConfig`（9 个键，`KEY_*` 常量：`dataType`/`byteOrder`/`isSigned`/`scale`/`offset`/`value`/`address`/`count`/`areaCode`），产出**单个 JSON 对象**载荷 `{areaCode,startAddress,count,words[],dataType}`（5 个键，`OUT_*` 常量），仍塞进 `EncodeMessage.modbusWriteJson` 返回（`isSend=true`）。
    - **`startAddress` / `words` 两个键与 FINS 刻意取同名**：载荷形态本来就一样（起始地址 + 字/位值列表），这样 api 侧 `ProtocolEncodeInvoker#parseWriteEcho` 的形态判别（含 `"words"` → 走 `parseWordsEcho`）不必为三菱再分一支。**回传槽仍是 `EncodeMessage.modbusWriteJson`** —— 与 S7/FINS 同样的历史包袱，改名要协议 jar 与 api 同时发版。
    - **字设备 / 位设备分叉**（严格镜像 api 侧 `MitsubishiMc3eDataReader` 的读法）：**字设备**（D 0xA8 / W 0xB4 / R 0xAF / ZR 0xB0 / SD 0xA9）复用 `RegisterValueUtils.encode`，值与 `count` 共同决定编码（count=1→16 位整型、2→int32/float32、4→int64/double64，与读侧 `convert` 完全对称）；**位设备**（M 0x90 / L 0x92 / B 0xA0 / X 0x9C / Y 0x9D / S 0x98 / SM 0x91 / F 0x93）只接受布尔值、产出 `[0]`/`[1]`，且 `count` 必须为 1。
    - **本侧比 api 侧更严**：两张码表都不认的软元件码**直接拒**（api 读侧把「非字设备」一律当位设备，读方向无所谓，**写方向会静默写错地方**）。上限自查用本侧 `MAX_WORD_COUNT=960` / `MAX_BIT_COUNT=2000`，不复用 `RegisterValueUtils.encode` 内部那个 123（那是 Modbus 功能码 16 的规范值，会把合法的 MC 字写挡掉）。
    - **⚠️ `byteOrder` 这个键在 MC 里是有意义的**（与 FINS 不同）：MC 帧内多字节字段是小端，api 侧读出来是宿主序 16bit 字列表，所以它只决定**多字值的字序**，与读侧 `convert` 同一判据，读得出来就写得进去。`byteOrder` 取值 `big`/`little`（也认 `大端`/`小端`/`BE`/`LE`），**空默认大端**（`RegisterValueUtils.isBigEndian`），物模型列 `byte_order` 的库默认值也是 `'big'` —— **三菱 MC 的多字点位（int32 / float32 / double64，即 `length≥2`）必须显式选「小端」**，否则跨字值的字序会反。单字点位（16 位整型）不受影响。读侧同此判据，所以「读得出来就写得进去」。
    - **⚠️ 软元件码表在本包内已有三份**（`MitsubishiMc3eTcpDeal`、`MitsubishiMc1eTcpDeal`，以及 api 侧 `MitsubishiMc3eDataReader`）—— 协议库与 api 是两个编译单元共享不了；协议库内部三菱这几种帧也**没做公共父类**（拆开的用意就是各自独立演化）。判错不报错、只是写错地方，改一处必须同步其余几处。
    - 编码异常 `IllegalArgumentException` 经 api 侧 `ProtocolEncodeInvoker` 反射还原为 **422**。
  - **`MITSUBISHI_MC1E_TCP`（`MitsubishiMc1eTcpProtocol` / `MitsubishiMc1eTcpDeal`，骨架）**：**协议库本层已就绪且正确** —— decode/encode 与 3E 那份同源（QnA 系列 1E 帧与 3E 帧**共用同一套命令**（批量读 `0x0401` / 批量写 `0x1401`，子命令 `0x0000` 字 / `0x0001` 位）**和同一张软元件码表**，差异只在帧头）。**缺的是 api 侧帧层**：`labdatahub-component/.../mitsubishi_mc1e_tcp` 组件包**尚不存在**（组帧、网络 I/O 都在那一层，协议库拿不到连接）⇒ **当前根本读不出数据**。**平台接线也一处没加**（`NetType` / `MAIN_CLASS_PATH` / component open-close case / `TimerTask` 起调度 / 建表 / 前端三处下拉与详情页 tab / `PointWriteMetaResolver` 准入 / `PointWriter` 分派），**因此它目前既上传不了也选不到，不会以「能选但连不上」的形态误导人**。真机到位后按类注释里的顺序补全。**⚠️ 组帧最易错的一处：1E 帧起始地址是 2 字节，3E 是 3 字节。**
  - **`MITSUBISHI_A1E_TCP`（`MitsubishiA1eTcpProtocol` / `MitsubishiA1eTcpDeal`，骨架）**：decode 可用（帧无关的值转换），**`encode` 恒抛 `UnsupportedOperationException`** —— A 兼容 1E 帧面向 A 系列，其**软元件码表与命令集和 QnA 系不是一套**，照着 QnA 的码表写会**静默写错软元件**，在确认 A 系帧格式前一律拒绝写入。**刻意不返回 `isSend=false` 的只读 stub**：那种写法在本平台语义里等于「写成功」，会让调用方以为值已经下去了，而这里是真没实现。

- **三菱 CNC（`MitsubishiCncTcpProtocol` / `MitsubishiCncTcpDeal`，netType/protocolType = `MITSUBISHI_CNC_TCP`）**：decode 与 FANUC 单值模式同构（2026-08-29 参照树根 `MitsubishiNcTcpPacket.dll` 逆向实现，**待真机验证**）——后端 `MitsubishiCncDataReader`（`lab-data-hub-api` 组件包 `mitsubishi_cnc_tcp`）按 MOCHA 帧（GIOP 风格，TCP 683）一次请求一个点位（`readType[.axisNo]`，轴点 axisNo 1-6=X/Y/Z/A/B/C），按响应 DataType 解析出**单个原始字符串**塞进 `value`，decode 收到 `code/deviceSn/value/dataType`，私有 `convertValue` 按 dataType（bool/int/long/float/double/string）转类型 + `RegisterValueUtils.applyScale`（byteOrder/isSigned 不适用），值塞 `properties.put(code, converted)`。协议库**不含网络 I/O、不感知 MOCHA 寻址表**。encode 为只读 stub（`isSend=false`）。

- **Modbus decode 收到的是合并后的单个连续块**：`ModbusMessageConsumeService` 把 `registerRange` 的多个子区间合并为 `[minStart, maxEnd]` 一块读取，decode 的 jsonArray 只有一个 item（code 键不再被多区间互覆盖）。`applySigned`/`applyScale` 按物模型解析参数套用；bool 类型按字非零转 0/1。
- **S7 decode 的 applySigned 只对 DBW（16 位整型）生效**：`S71200TcpDeal.decode` 收到 `blockType`，仅 `blockType == null || "DBW"` 时套 `applySigned`——因为 `applySigned` 做 16 位符号扩展，对 DBD 的 DINT 32 位整型（低 16 位 >0x7FFF 时）会截断成负数，DBD/DBB 直接 `applyScale`。DBD 的 int 系列解码（手动 4 字节大端 int32 / float32）在 `lab-data-hub-api` 的 `S7DataReader` 侧完成，decode 收到的已是原始值。

- **S7 写值 encode（2026-09-18 实装，netType = `S71200_TCP`）**：`S71200TcpDeal.encode` 收到扁平 `otherConfig`（12 个键，`KEY_*` 常量：`dataType`/`byteOrder`/`isSigned`/`scale`/`offset`/`value`/`address`/`dbNumber`/`areaType`/`blockType`/`bitOffset`/`length`），按块类型编码成字节后组**单个 JSON 对象**载荷 `{areaType, dbNumber, address, bitOffset, blockType, dataType, bytes[]}`（7 个键，`OUT_*` 常量），塞进 `EncodeMessage.modbusWriteJson` 返回（`isSend=true`）。
  - **⚠️ 三处跨 classloader 字符串契约**（api 侧与协议 jar 无编译期检查，改一边必须同步另一边）：① 入向 `OpenApiEncodeKeys.*` ↔ 本侧 `KEY_*`；② 出向 `OUT_*` ↔ api 侧 `S7PointWriter.KEY_*`；③ 回传槽仍是 `EncodeMessage.modbusWriteJson`（字段名沿用 Modbus，未改名——协议 jar 与 api 是同一份历史包袱，改名要两边同时发版）。**Map value 只能放 JDK 标量**。
  - **⚠️ fastjson2 默认把 `byte[]` 序列化成 base64**（`WriteByteArrayAsBase64`）→ 载荷里的 `bytes` 必须显式转 `List<Integer>`（0-255 无符号，`toUnsignedList(byte[])`），否则 api 侧认不出。
  - **字节编码矩阵（严格镜像 api 侧 `S7DataReader` 的读法，读得出来就写得进去）**：`DBW` 恒 2 字节 16 位整型（**无视 dataType**）；`DBD` 4 字节，`isIntType(dataType)`（int/integer/short/long/byte）→ DINT int32，否则 Real float32；`DBX` 恒位（1 字节 0/1 + `bitOffset` 0-7，越界抛 `IllegalArgumentException`）；`DBB` 恒西门子 STRING（`[0]`=最大长度=length-2、`[1]`=实际长度、内容从 index 2 起、**GBK** 编码——读侧就是 GBK 解码，只拿英文测永远发现不了）。全部大端。不支持块类型/缺块类型直接抛异常。
  - `RegisterValueUtils.encodeBytes(value, dataType, byteOrder, isSigned, scale, offset, byteCount)` 是写方向的数值编码入口：反缩放（`scale=0` 拒绝）→ 按 2/4 字节查 `isIntType` 定整型或浮点 → `integerToBytes`。`parseBoolean` 提为 public 供 DBX 复用。**`isIntType` 在 api 侧 `S7DataReader` 有一份等价实现，两处是同一判据的两份代码。**
  - 编码异常经 api 侧 `ProtocolEncodeInvoker` 反射还原：`IllegalArgumentException` → **422**，其余 → 500。
  - ⚠️ **本侧 encode 只产字节，不做读-改-写**：DBX 的「先读原字节再置/清位」由 api 侧 `S7PointWriter.writeBit` 完成（协议库不含网络 I/O，拿不到连接）。`bitOffset` 原样放进载荷由 api 侧消费。
- **FINS 写值 encode（2026-09-19 实装，netType = `OMRONFINS_TCP`）**：`OmronFinsTcpDeal.encode` 收到扁平 `otherConfig`（10 个键，`KEY_*` 常量：`dataType`/`byteOrder`/`isSigned`/`scale`/`offset`/`value`/`address`/`count`/`areaCode`/`bitAddress`），产出**单个 JSON 对象**载荷 `{areaCode, startAddress, bitAddress, count, words[], dataType}`（6 个键，`OUT_*` 常量），仍塞进 `EncodeMessage.modbusWriteJson` 返回（`isSend=true`）。**载荷形态与 Modbus 的 `RangeItem` 数组不同** —— Modbus 一条载荷可覆盖多个区间，FINS 一条载荷就是一次内存区写命令。
  - **字区/位区分叉**（严格镜像 api 侧 `FinsDataReader` 的读法）：**字区**复用 `RegisterValueUtils.encode`，值与 `count` 共同决定编码（count=1→16 位整型、2→float32/int32、4→double64/int64，与读侧 `convert` 完全对称）；**位区**只接受布尔值（`RegisterValueUtils.parseBoolean`），产出 `[0]`/`[1]`，且 `count` 必须为 1（一个位点位就是一个值，不存在「一次写多个位」的业务场景）。字区载荷不带 `bitAddress` 键（fastjson2 默认不输出 null）。
  - **上限自查用自己的 `MAX_ITEMS = 1000`**，不复用 `RegisterValueUtils.encode` 内部那个 123 —— 那个数是 Modbus 功能码 16 的规范值，拿它拦 FINS 会把合法点位挡掉，错误文案还会提到不相干的功能码。位区一次只写 1 位，故同一上限够用。
  - **本侧 encode 只产字值/位值，不做读-改-写**：FINS 有原生的位写命令（区码换成位区码 + 地址第 3 字节填位号即可），**不需要 S7 那种「先读 1 字节 → 置/清位 → 写回」**，一次调用完成，不存在 S7 那种「对 PLC 用户程序不原子」的窗口。地址范围/位号范围的校验在 api 侧 `FinsPointWriter.parsePayload`（协议库不感知连接，拿不到设备反馈）。
  - **⚠️ `isBitArea` 是同一套判据的多份实现**：本侧 `OmronFinsTcpDeal.isBitArea`、api 侧 `FinsDataReader.isBitArea`、controller `checkConfig`、前端 `OmronFinsTcpConfig.vue` 的 `BIT_AREA_ITEMS`，共四处。协议库与 api 是两个编译单元、共享不了，**改一处必须同步其余几处** —— 判错不是报错而是把位当字读/写，静默读写到另一个区。
  - **区码勘误（2026-09-19）**：旧「IR区 0x88」已删除 —— 0x88 实为 **CNT 计数器区**（CS/CJ 系列根本没有 IR 区）；旧「H区 0x31」也是错的（0x31 是 WR 的**位**码）。标准区码**成对出现**（字访问码 / 位访问码，依据 W342 内存区指定表）：CIO `0xB0`/`0x30`、WR `0xB1`/`0x31`、HR(H) `0xB2`/`0x32`、AR(A) `0xB3`/`0x33`、DM `0x82`/`0x02`、EM 当前库 `0x98`/`0x18`、EM 库 0-15 `0xA0-0xAF`/`0x20-0x2F`。
  - 编码异常 `IllegalArgumentException` 经 api 侧 `ProtocolEncodeInvoker` 反射还原为 **422**。
- **写值能力一览（2026-09-19）**：

  | 协议 | encode 行为 |
  |---|---|
  | `MODBUS_TCP` / `S71200_TCP` / `OMRONFINS_TCP` / `MITSUBISHI_MC3E_TCP` | **已实装**，产出载荷、`isSend=true` |
  | Brother / FANUC / MitsubishiCnc | **只读 stub**，返回 `isSend=false` |
  | `MITSUBISHI_A1E_TCP` | **未实现**，恒抛 `UnsupportedOperationException`（不是 stub —— stub 在本平台语义里等于「写成功」，那是谎报） |

  api 侧 `PointWriteMetaResolver` 第 4 步按 netType 准入，**非上面四个已实装协议一律 409**，不会走到 stub 与 A1E 的抛错分支。所以那两条路径目前都不可达，属于「将来接通时的正确失败方式」。

Brother 的 **PDSP 语义点位表（语义点位 → 地址映射）为前端/后端配置层职责，协议库不感知**：前端 `lab-data-hub-web/src/utils/brotherTcpPoints.js` 内置点表，配置 code 填点位语义 key；本库只按后端传入的 `code/value/dataType` 解析，地址约定仍是 `数据区.行号.字段序号`，无协议库改动。

**FANUC FOCAS2（`FanucFocasProtocol` / `FanucFocasDeal`，netType/protocolType = `FANUC_TCP`）**：协议库仍只做解码（decode 按 `code/deviceSn/value/dataType` 解析，地址 = `采集项类型.参数1.参数2`，如 `axis.1.1` = X 轴机械坐标），**不感知 FOCAS2 地址、不含网络 I/O/JNA**。FOCAS2 的实际采集（fwlib32 JNA 绑定、连接、调度、自愈）全部在 `lab-data-hub-api` 的 `labdatahub-component` 模块 `com.labdatahub.component.fanuc_focas` 包，协议库 decode 收到的是该包 `FanucFocasDataReader` 按点位读出的原始字符串，与 Brother 一致按 dataType 转类型 + `applyScale`。

## 构建

```bash
mvn clean install -DskipTests
```

产出 jar 后，按 `lab-data-hub-api/README.md` 说明放入其 `protocol/` 目录供后端加载。

## 编码规范

1. **所有 class 字段必须加中文注释**。
2. 列表循环尽量用增强 for，少用下标循环。
3. 新增协议遵循现有 `parent/` + `deal/` 两段式结构：先在 `parent` 定义协议基类，再在 `deal` 实现具体处理。
