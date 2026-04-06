package com.apitestagent.persistence.mapper;

import org.apache.ibatis.annotations.Mapper;

import com.apitestagent.persistence.entity.AgentTaskEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

@Mapper
public interface AgentTaskMapper extends BaseMapper<AgentTaskEntity> {
}