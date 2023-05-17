package com.wggt.compare.pojo;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Scan {
    private Integer id;  // 'ID'
    private String url;  //'码流url',
    private Short subRow;  //'子块行数, 说明: 1 8行, 2 16行',
    private Short subCol; // '子块列数, 说明: 1 32列, 2 64列',
    private Short near;  // '微损度, 说明: 0 near=0, 1 near=1, 2 near=2, 3 near=3',
    private Short channel;  // '通道标识, 说明: 0 中波, 3 短波',
    private Short checkerrMode;  // '检纠错模式, 说明: 0 无纠错模式, 1 纠错模式1, 2 纠错模式2',
    private Integer frameCount;  //  '压缩帧计数',    
    private LocalDateTime createTime; //  '创建时间',
    private LocalDateTime updateTime;  // '修改时间'
}
