package com.labdatahub.protocol.message;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/**
 * @Description: modbus寄存器项
 * @Author: chen tao
 * @CreateTime: 2025-12-30
 */
@Data
@AllArgsConstructor
public class RangeItem {
    // 开始位置
    private int start;
    // 数量
    private int count;
    //寄存器数据
    private List<Integer> registerList;
}
