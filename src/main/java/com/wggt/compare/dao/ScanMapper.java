package com.wggt.compare.dao;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import com.wggt.compare.pojo.Scan;

@Mapper
public interface ScanMapper {

    /**
     * 将全链路对比源批量插入数据库
     * @param srcLists
     */
    public void save(List<Scan> srcLists);

    /**
     * 查询给定条件的全链路对比源
     * @param target
     * @return
     */
    @Select("select * from scan where url like concat(#{url}, '%') and sub_row = #{subRow}" +  
            " and sub_col = #{subCol} and near = #{near} and channel = #{channel} and checkerr_mode = #{checkerrMode}")
    public List<Scan> selectAll(Scan target);
}
