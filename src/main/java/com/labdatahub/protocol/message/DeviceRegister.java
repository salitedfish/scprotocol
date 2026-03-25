package com.labdatahub.protocol.message;

import lombok.Data;

/**
 * @Description: 设备自注册
 * @Author: labdatahub
 * @CreateTime: 2025-09-19
 */
@Data
public class DeviceRegister {
    /**产品sn*/
    private String productSn;
    /**设备sn*/
    private String deviceSn;
    /**设备名称*/
    private String deviceName;
    /**是否继承产品属性,默认true*/
    private Boolean extendsFromProduct = true;
}
