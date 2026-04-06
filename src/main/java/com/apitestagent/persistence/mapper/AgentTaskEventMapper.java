package com.apitestagent.persistence.mapper;

import org.apache.ibatis.annotations.Mapper;

import com.apitestagent.persistence.entity.AgentTaskEventEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

@Mapper
public interface AgentTaskEventMapper extends BaseMapper<AgentTaskEventEntity> {
}