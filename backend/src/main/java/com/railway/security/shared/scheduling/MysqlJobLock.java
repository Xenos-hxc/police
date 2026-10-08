package com.railway.security.shared.scheduling;

import java.sql.SQLException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class MysqlJobLock {
    private final JdbcTemplate jdbc;

    // MySQL 命名锁绑定连接，获取与释放必须使用同一连接；它不是行锁，也不会仅因事务提交而自动释放。
    public void runIfLeader(String name, Runnable job) {
        if (!name.matches("[a-z0-9:_-]{1,64}")) {
            throw new IllegalArgumentException("Invalid MySQL job lock name");
        }
        jdbc.execute(
                (ConnectionCallback<Void>)
                        connection -> {
                            try (var statement =
                                    connection.prepareStatement("SELECT GET_LOCK(?, 0)")) {
                                statement.setString(1, name);
                                try (var result = statement.executeQuery()) {
                                    if (!result.next() || result.getInt(1) != 1) {
                                        log.debug("跳过其他节点正在执行的任务：{}", name);
                                        return null;
                                    }
                                }
                            }
                            try {
                                job.run();
                            } finally {
                                release(connection, name);
                            }
                            return null;
                        });
    }

    private void release(java.sql.Connection connection, String name) {
        try (var statement = connection.prepareStatement("SELECT RELEASE_LOCK(?)")) {
            statement.setString(1, name);
            statement.executeQuery().close();
        } catch (SQLException exception) {
            log.error("释放数据库任务锁失败：{}", name, exception);
        }
    }
}
