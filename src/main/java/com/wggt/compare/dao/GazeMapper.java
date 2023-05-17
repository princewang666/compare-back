package com.wggt.compare.dao;

import java.util.List;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import com.wggt.compare.pojo.Gaze;

@Mapper
public interface GazeMapper {

    /**
     * 根据传入的url查询对比源数据库数据
     * @param url
     * @return
     */
    @Select("select * from gaze where url = #{url}")
    public Gaze selectByUrl(String url);

    /**
     * 根据传入的gaze插入到全链路中
     * @param gaze
     */
    @Insert("insert into gaze (url, near, channel, checkerr_mode, frame_count, create_time, update_time)" + 
    " values (#{url},#{near},#{channel},#{checkerrMode},#{frameCount},#{createTime},#{updateTime})")
    public void insert(Gaze gaze);

    @Select("select * from gaze where url like concat(#{url}, '%') " +  
    " and near = #{near} and channel = #{channel} and checkerr_mode = #{checkerrMode}")
    public List<Gaze> selectExceptFrameCount(Gaze target);
}