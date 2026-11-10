package com.relayops.assistant.adapter.out.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.time.OffsetDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface AssistantMemoryFactMapper extends BaseMapper<AssistantMemoryFactEntity> {

    @Select("""
            select * from assistant_memory_fact
             where namespace = #{namespace} and scope_type = #{scopeType} and scope_key = #{scopeKey}
               and (expires_at is null or expires_at > now())
             order by confidence desc, coalesce(last_used_at, created_at) desc
             limit #{limit}
            """)
    List<AssistantMemoryFactEntity> selectActive(@Param("namespace") String namespace,
                                                 @Param("scopeType") String scopeType,
                                                 @Param("scopeKey") String scopeKey,
                                                 @Param("limit") int limit);

    @Select("""
            select * from assistant_memory_fact
             where namespace = #{namespace} and scope_type = #{scopeType} and scope_key = #{scopeKey}
               and (expires_at is null or expires_at > now())
               and (fact_value ilike #{pattern} or coalesce(fact_key, '') ilike #{pattern})
             order by confidence desc, coalesce(last_used_at, created_at) desc
             limit #{limit}
            """)
    List<AssistantMemoryFactEntity> searchByKeyword(@Param("namespace") String namespace,
                                                    @Param("scopeType") String scopeType,
                                                    @Param("scopeKey") String scopeKey,
                                                    @Param("pattern") String pattern,
                                                    @Param("limit") int limit);

    @Select("""
            select e.fact_id as factId, (e.embedding <=> cast(#{vector} as vector)) as distance
              from assistant_memory_embedding e
              join assistant_memory_fact f on f.id = e.fact_id
             where e.model = #{model} and e.dim = #{dim}
               and f.namespace = #{namespace} and f.scope_type = #{scopeType} and f.scope_key = #{scopeKey}
               and (f.expires_at is null or f.expires_at > now())
             order by distance asc
             limit #{limit}
            """)
    List<MemoryMatchRow> selectSimilar(@Param("namespace") String namespace,
                                       @Param("scopeType") String scopeType,
                                       @Param("scopeKey") String scopeKey,
                                       @Param("model") String model,
                                       @Param("dim") int dim,
                                       @Param("vector") String vector,
                                       @Param("limit") int limit);

    @Insert("""
            insert into assistant_memory_embedding (fact_id, model, dim, embedding)
            values (#{factId}, #{model}, #{dim}, cast(#{vector} as vector))
            on conflict (fact_id, model) do update
               set embedding = excluded.embedding,
                   dim = excluded.dim
            """)
    int upsertEmbedding(@Param("factId") long factId,
                        @Param("model") String model,
                        @Param("dim") int dim,
                        @Param("vector") String vector);

    @Update("""
            <script>
            update assistant_memory_fact set last_used_at = #{now}
             where id in
            <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>
            </script>
            """)
    int touch(@Param("ids") List<Long> ids, @Param("now") OffsetDateTime now);

    @Select("select count(*) from assistant_memory_fact where expires_at is not null and expires_at <= now()")
    int countExpired();

    @Select("delete from assistant_memory_fact where expires_at is not null and expires_at <= now()")
    int deleteExpired();
}
