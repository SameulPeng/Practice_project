package com.practice.config;

import org.apache.kafka.common.record.CompressionType;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.ConfigurationFactory;
import org.apache.logging.log4j.core.config.ConfigurationSource;
import org.apache.logging.log4j.core.config.Order;
import org.apache.logging.log4j.core.config.builder.api.*;
import org.apache.logging.log4j.core.config.builder.impl.BuiltConfiguration;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.springframework.util.ClassUtils;

import java.io.*;
import java.net.URI;
import java.util.HashMap;

/**
 *  日志配置工厂类<br/>
 *  生效条件<br/>
 *  1. 在 application.properties 配置文件中设置配置文件路径 logging.config=classpath:logging.cfg<br/>
 *  2. 在 resources 目录下创建 logging.cfg 文件<br/>
 *  覆盖条件<br/>
 *  1. 更改或移除配置文件路径 logging.config=...<br/>
 *  2. 在上述路径或 resources 目录下创建 xml、json、yaml、properties 等配置文件<br/>
 */
/*
    原理
    由ConfigurationFactory类的静态内部类Factory的getConfiguration(LoggerContext, String, URI)方法确定配置工厂类
        如果没有设置配置文件路径，则按优先级从高到低遍历配置工厂类，找到第一个支持类型包含全类型"*"的配置工厂类
        如果设置了配置文件路径，则按优先级从高到低遍历配置工厂类，找到第一个支持类型包含全类型"*"或配置文件扩展名的配置工厂类
        默认配置工厂类
            当前自定义 优先级 9 支持类型 cfg
            Properties 优先级 8 支持类型 properties
            Yaml 优先级 7 支持类型 yaml yml
            JSON 优先级 6 支持类型 json jsn
            XML 优先级 5 支持类型 xml *
            SpringBoot 优先级 0 支持类型 .springboot
    因此当前自定义配置工厂类在设置了配置项 logging.config=classpath:logging.cfg 时生效，并从中读取配置项值，覆盖默认值
 */
@Plugin(name = "Log4j2ConfigFactory", category = ConfigurationFactory.CATEGORY)
// 指定配置工厂类的优先级，数值越大，优先级越高
// 原有配置工厂类的优先级，SpringBoot自动配置优先级为0，xml、json、yaml、properties等文件配置优先级分别为5、6、7、8
@Order(9)
public class Log4j2ConfigFactory extends ConfigurationFactory {
    private String logPattern = // 日志格式
            "%d{yyyy-MM-dd HH:mm:ss.SSS} %highlight{%-5.5p} [%-20.20t] %-60.60c{1.}:   %m%n";
    private String dataPattern = // 大数据格式
            "%m";
    private String dirPath = // 日志文件目录
            "./logs/";
    private String filePattern = // 日志文件名称格式
            "%d{yyyy-MM-dd}/%d{yyyy-MM-dd-HH}_%i.log.gz";
    private boolean toConsole = true; // 是否输出日志到控制台
    private boolean toFile = false; // 是否输出日志到文件
    private boolean toBigData = false; // 是否输出日志到大数据接口

    public Log4j2ConfigFactory() {
        HashMap<String, String> params = new HashMap<>();
        // 读取 resources 目录下的 logging.cfg 配置文件
        try (BufferedReader br = new BufferedReader(new FileReader(
                ClassUtils.getDefaultClassLoader().getResource("").getPath() + "logging.cfg"))) {
            String line;
            while((line = br.readLine()) != null) {
                // 忽略注释行和空行
                if (line.length() == 0 || line.trim().startsWith("#")) continue;
                // 解析键值对
                String[] kv = line.split("=", 2);
                String key = kv[0];
                String value = kv[1];
                if (key != null && key.length() > 0 && value != null && value.length() > 0) {
                    params.put(key, value);
                }
            }
        } catch (Exception ignore) {}

        String value;
        if (params.size() > 0) {
            if ((value = params.get("log-pattern")) != null) this.logPattern = value;
            if ((value = params.get("data-pattern")) != null) this.dataPattern = value;
            if ((value = params.get("dir-path")) != null) this.dirPath = value;
            if ((value = params.get("file-pattern")) != null) this.filePattern = value;
            if ((value = params.get("to-console")) != null) this.toConsole = Boolean.parseBoolean(value);
            if ((value = params.get("to-file")) != null) this.toFile = Boolean.parseBoolean(value);
            if ((value = params.get("to-big-data")) != null) this.toBigData = Boolean.parseBoolean(value);

            if ((value = params.get("file-warn.size-each-file")) != null) FileWarnParams.sizeEachFile = value;
            if ((value = params.get("file-warn.count-each-day")) != null) FileWarnParams.countEachDay = Integer.parseInt(value);
            if ((value = params.get("file-warn.days-kept")) != null) FileWarnParams.daysKept = value;
            if ((value = params.get("file-warn.size-kept")) != null) FileWarnParams.sizeKept = value;
            if ((value = params.get("file-warn.count-kept")) != null) FileWarnParams.countKept = Integer.parseInt(value);

            if ((value = params.get("file-biz.size-each-file")) != null) FileBizParams.sizeEachFile = value;
            if ((value = params.get("file-biz.days-kept")) != null) FileBizParams.daysKept = value;
            if ((value = params.get("file-biz.size-kept")) != null) FileBizParams.sizeKept = value;
            if ((value = params.get("file-biz.count-kept")) != null) FileBizParams.countKept = Integer.parseInt(value);

            if ((value = params.get("file-info.size-each-file")) != null) FileInfoParams.sizeEachFile = value;
            if ((value = params.get("file-info.count-each-day")) != null) FileInfoParams.countEachDay = Integer.parseInt(value);
            if ((value = params.get("file-info.days-kept")) != null) FileInfoParams.daysKept = value;
            if ((value = params.get("file-info.size-kept")) != null) FileInfoParams.sizeKept = value;
            if ((value = params.get("file-info.count-kept")) != null) FileInfoParams.countKept = Integer.parseInt(value);

            if ((value = params.get("kafka.bootstrap-servers")) != null) BigDataParams.bootstrapServers = value;
            if ((value = params.get("kafka.topic")) != null) BigDataParams.topic = value;
            if ((value = params.get("kafka.key")) != null) BigDataParams.key = value;
            if ((value = params.get("kafka.sync-send")) != null) BigDataParams.syncSend = Boolean.parseBoolean(value);
            if ((value = params.get("kafka.buffer-memory")) != null) BigDataParams.bufferMemory = Long.parseLong(value);
            if ((value = params.get("kafka.batch-size")) != null) BigDataParams.batchSize = Long.parseLong(value);
            if ((value = params.get("kafka.linger-ms")) != null) BigDataParams.lingerMs = Long.parseLong(value);
            if ((value = params.get("kafka.acks")) != null) BigDataParams.acks = Integer.parseInt(value);
            if ((value = params.get("kafka.retries")) != null) BigDataParams.retries = Integer.parseInt(value);
            if ((value = params.get("kafka.request-timeout-ms")) != null) BigDataParams.requestTimeoutMS = Long.parseLong(value);
            if ((value = params.get("kafka.compression-type")) != null) {
                try {
                    BigDataParams.compressionType = CompressionType.forName(value);
                } catch (IllegalArgumentException e) {
                    BigDataParams.compressionType = CompressionType.NONE;
                }
            }
        }
    }

    /**
     * 错误日志文件（WARN及以上日志级别）配置
     */
    private static class FileWarnParams {
        private static String sizeEachFile = "200 MB"; // 单个文件最大大小
        private static int countEachDay = 10; // 单日文件最大数量
        private static String daysKept = "7d"; // 文件保留天数
        private static String sizeKept = "10 GB"; // 文件最大总大小
        private static int countKept = 100; // 文件最大总数量
    }

    /**
     * 业务日志文件（BIZ日志级别）配置
     */
    public static class FileBizParams {
        private static String sizeEachFile = "200 MB"; // 单个文件最大大小
        private static String daysKept = "30d"; // 文件保留天数
        private static String sizeKept = "100 GB"; // 文件最大总大小
        private static int countKept = 1000; // 文件最大总数量
    }

    /**
     * 普通日志文件（INFO日志级别）配置
     */
    private static class FileInfoParams {
        private static String sizeEachFile = "200 MB"; // 单个文件最大大小
        private static int countEachDay = 10; // 单日文件最大数量
        private static String daysKept = "7d"; // 文件保留天数
        private static String sizeKept = "10 GB"; // 文件最大总大小
        private static int countKept = 100; // 文件最大总数量
    }

    /**
     * 大数据接口（BIGDATA日志级别）配置
     */
    private static class BigDataParams {
        private static String bootstrapServers = "localhost:9092"; // Kafka 服务器地址
        private static String topic = "red-packet-big-data"; // Kafka 主题
        private static String key = null; // Kafka 消息key
        private static boolean syncSend = false; // Kafka 是否同步发送
        private static long bufferMemory = 64 << 10 << 10; // Kafka 缓冲区大小
        private static long batchSize = 32 << 10; // Kafka 缓冲区数据批次大小
        private static long lingerMs = 1000; // Kafka 缓冲区数据拉取延迟时间
        private static int acks = -1; // Kafka 生产应答方式
        private static int retries = 3; // Kafka 重试次数
        private static long requestTimeoutMS = 1000; // Kafka 请求超时时间
        private static CompressionType compressionType = CompressionType.NONE; // Kafka 压缩方式
    }

    @Override
    protected String[] getSupportedTypes() {
        // 支持类型，匹配配置文件扩展名
        // 不指定配置文件路径时（logging.config配置项），优先级最高且支持类型包含全类型"*"的配置工厂类生效
        // 指定配置文件路径时（logging.config配置项），优先级最高，且支持类型包含全类型"*"或指定配置文件扩展名的配置工厂类生效
        return new String[] {"cfg"};
    }

    @Override
    public Configuration getConfiguration(LoggerContext loggerContext, ConfigurationSource source) {
        return getConfiguration(loggerContext, source.toString(), null);
    }

    @Override
    public Configuration getConfiguration(LoggerContext loggerContext, String name, URI configLocation) {
        ConfigurationBuilder<BuiltConfiguration> builder = newConfigurationBuilder();
        return createConfiguration(builder);
    }

    /**
     * 编程式构造配置<br/>
     * 配置的层次结构与xml等配置文件的层次结构相同，具体参考xml配置方式
     */
    private Configuration createConfiguration(ConfigurationBuilder<BuiltConfiguration> builder) {
        // <Configuration>
        builder.setConfigurationName("RedPacket")
                .setStatusLevel(Level.INFO)
                .setMonitorInterval("0");

        // <CustomLevels>
        /*
            原有日志级别和对应的级别数值
            OFF     0
            FATAL   100
            ERROR   200
            WARN    300
            INFO    400
            DEBUG   500
            TRACE   600
            ALL     pow(2, 31) - 1
         */
        builder.add(builder.newCustomLevel("BIZ", 350))
                // BIGDATA日志级别的内容需要创建对象，为了便于启用惰性日志，设置其日志级别低于INFO而高于DEBUG
                .add(builder.newCustomLevel("BIGDATA", 450));

        // <Loggers>
        RootLoggerComponentBuilder rootLogger =
                builder.newAsyncRootLogger(Level.INFO);

        LoggerComponentBuilder redPacketLogger =
                builder.newAsyncLogger("com.practice", Level.INFO)
                        .addAttribute("additivity", true);

        // <Appenders> & <AppenderRef>
        if (toConsole) {
            builder.add(createConsoleAppender(builder));
            rootLogger.add(builder.newAppenderRef("console"));
        }
        if (toFile) {
            builder.add(createFileWarnAppender(builder))
                    .add(createFileBizAppender(builder))
                    .add(createFileInfoAppender(builder));
            rootLogger.add(builder.newAppenderRef("file-warn"))
                    .add(builder.newAppenderRef("file-biz"))
                    .add(builder.newAppenderRef("file-info"));
        }
        if (toBigData) {
            builder.add(createKafkaAppender(builder));
            rootLogger.add(builder.newAppenderRef("big-data"));
            redPacketLogger =
                    builder.newAsyncLogger("com.practice", "BIGDATA")
                            .addAttribute("additivity", true);
        }

        builder.add(rootLogger)
                .add(redPacketLogger);

        return builder.build(true);
    }

    /**
     * 输出到控制台的Appender组件配置
     */
    private AppenderComponentBuilder createConsoleAppender(ConfigurationBuilder<BuiltConfiguration> builder) {
        // <Layout>
        LayoutComponentBuilder consoleLayout =
                builder.newLayout("PatternLayout")
                        .addAttribute("pattern", logPattern)
                        .addAttribute("disableAnsi", false);

        // <Filter>
        FilterComponentBuilder consoleThresholdFilter =
                builder.newFilter("ThresholdFilter", Filter.Result.NEUTRAL, Filter.Result.DENY)
                        .addAttribute("level", Level.INFO);

        // <Appender>
        return builder.newAppender("console", "Console")
                .addComponent(consoleLayout)
                .addComponent(consoleThresholdFilter);
    }

    /**
     * 输出到错误日志文件的Appender组件配置
     */
    @SuppressWarnings("rawtypes")
    private AppenderComponentBuilder createFileWarnAppender(ConfigurationBuilder<BuiltConfiguration> builder) {
        // <Layout>
        LayoutComponentBuilder fileLayout =
                builder.newLayout("PatternLayout")
                        .addAttribute("pattern", logPattern);

        // <Filter>
        FilterComponentBuilder thresholdFilter =
                builder.newFilter("ThresholdFilter", Filter.Result.NEUTRAL, Filter.Result.DENY)
                        .addAttribute("level", Level.WARN);

        // <Policies>
        ComponentBuilder timePolicy =
                builder.newComponent("TimeBasedTriggeringPolicy")
                        .addAttribute("interval", 6)
                        .addAttribute("modulate", true);

        ComponentBuilder sizePolicy =
                builder.newComponent("SizeBasedTriggeringPolicy")
                        .addAttribute("size", FileWarnParams.sizeEachFile);

        ComponentBuilder policies =
                builder.newComponent("Policies")
                        .addComponent(timePolicy)
                        .addComponent(sizePolicy);

        // <RolloverStrategy>
        ComponentBuilder RolloverStrategy =
                builder.newComponent("DefaultRolloverStrategy")
                        .addAttribute("max", FileWarnParams.countEachDay)
                        .addComponent(
                                builder.newComponent("Delete")
                                        .addAttribute("basePath", dirPath)
                                        .addAttribute("maxDepth", 3)
                                        .addComponent(
                                                // 清理逻辑：文件名匹配 && (文件过期 || 文件大小超过上限 || 文件数量超过上限)
                                                builder.newComponent("IfFileName")
                                                        .addAttribute("glob", "warn/*/*.log.gz")
                                                        .addComponent(
                                                                builder.newComponent("IfAny")
                                                                        .addComponent(
                                                                                builder.newComponent("IfLastModified")
                                                                                        .addAttribute("age", FileWarnParams.daysKept)
                                                                        )
                                                                        .addComponent(
                                                                                builder.newComponent("IfAccumulatedFileSize")
                                                                                        .addAttribute("exceeds", FileWarnParams.sizeKept)
                                                                        )
                                                                        .addComponent(
                                                                                builder.newComponent("IfAccumulatedFileCount")
                                                                                        .addAttribute("exceeds", FileWarnParams.countKept)
                                                                        )
                                                        )
                                        )
                        );

        // <Appender>
        return builder.newAppender("file-warn", "RollingFile")
                .addAttribute("fileName", dirPath + "warn/" + "warn.log")
                .addAttribute("filePattern", dirPath + "warn/" + filePattern)
                .addComponent(fileLayout)
                .addComponent(thresholdFilter)
                .addComponent(policies)
                .addComponent(RolloverStrategy);
    }

    /**
     * 输出到业务日志文件的Appender组件配置
     */
    @SuppressWarnings("rawtypes")
    private AppenderComponentBuilder createFileBizAppender(ConfigurationBuilder<BuiltConfiguration> builder) {
        // <Layout>
        LayoutComponentBuilder fileLayout =
                builder.newLayout("PatternLayout")
                        .addAttribute("pattern", logPattern);

        // <Filter>
        FilterComponentBuilder levelMatchFilter =
                builder.newFilter("LevelMatchFilter", Filter.Result.NEUTRAL, Filter.Result.DENY)
                        .addAttribute("level", "BIZ");

        // <Policies>
        ComponentBuilder timePolicy =
                builder.newComponent("TimeBasedTriggeringPolicy")
                        .addAttribute("interval", 1)
                        .addAttribute("modulate", true);

        ComponentBuilder sizePolicy =
                builder.newComponent("SizeBasedTriggeringPolicy")
                        .addAttribute("size", FileBizParams.sizeEachFile);

        ComponentBuilder policies =
                builder.newComponent("Policies")
                        .addComponent(timePolicy)
                        .addComponent(sizePolicy);

        // <RolloverStrategy>
        ComponentBuilder RolloverStrategy =
                builder.newComponent("DefaultRolloverStrategy")
                        .addAttribute("fileIndex", "nomax")
                        .addComponent(
                                builder.newComponent("Delete")
                                        .addAttribute("basePath", dirPath)
                                        .addAttribute("maxDepth", 3)
                                        .addComponent(
                                                // 清理逻辑：文件名匹配 && (文件过期 || 文件大小超过上限 || 文件数量超过上限)
                                                builder.newComponent("IfFileName")
                                                        .addAttribute("glob", "biz/*/*.log.gz")
                                                        .addComponent(
                                                                builder.newComponent("IfAny")
                                                                        .addComponent(
                                                                                builder.newComponent("IfLastModified")
                                                                                        .addAttribute("age", FileBizParams.daysKept)
                                                                        )
                                                                        .addComponent(
                                                                                builder.newComponent("IfAccumulatedFileSize")
                                                                                        .addAttribute("exceeds", FileBizParams.sizeKept)
                                                                        )
                                                                        .addComponent(
                                                                                builder.newComponent("IfAccumulatedFileCount")
                                                                                        .addAttribute("exceeds", FileBizParams.countKept)
                                                                        )
                                                        )
                                        )
                        );

        // <Appender>
        return builder.newAppender("file-biz", "RollingFile")
                .addAttribute("fileName", dirPath + "biz/" + "biz.log")
                .addAttribute("filePattern", dirPath + "biz/" + filePattern)
                .addComponent(fileLayout)
                .addComponent(levelMatchFilter)
                .addComponent(policies)
                .addComponent(RolloverStrategy);
    }

    /**
     * 输出到普通日志文件的Appender组件配置
     */
    @SuppressWarnings("rawtypes")
    private AppenderComponentBuilder createFileInfoAppender(ConfigurationBuilder<BuiltConfiguration> builder) {
        // <Layout>
        LayoutComponentBuilder fileLayout =
                builder.newLayout("PatternLayout")
                        .addAttribute("pattern", logPattern);

        // <Filter>
        FilterComponentBuilder levelMatchFilter =
                builder.newFilter("LevelMatchFilter", Filter.Result.NEUTRAL, Filter.Result.DENY)
                        .addAttribute("level", Level.INFO);

        // <Policies>
        ComponentBuilder timePolicy =
                builder.newComponent("TimeBasedTriggeringPolicy")
                        .addAttribute("interval", 6)
                        .addAttribute("modulate", true);

        ComponentBuilder sizePolicy =
                builder.newComponent("SizeBasedTriggeringPolicy")
                        .addAttribute("size", FileInfoParams.sizeEachFile);

        ComponentBuilder policies =
                builder.newComponent("Policies")
                        .addComponent(timePolicy)
                        .addComponent(sizePolicy);

        // <RolloverStrategy>
        ComponentBuilder RolloverStrategy =
                builder.newComponent("DefaultRolloverStrategy")
                        .addAttribute("max", FileInfoParams.countEachDay)
                        .addComponent(
                                builder.newComponent("Delete")
                                        .addAttribute("basePath", dirPath)
                                        .addAttribute("maxDepth", 3)
                                        .addComponent(
                                                // 清理逻辑：文件名匹配 && (文件过期 || 文件大小超过上限 || 文件数量超过上限)
                                                builder.newComponent("IfFileName")
                                                        .addAttribute("glob", "info/*/*.log.gz")
                                                        .addComponent(
                                                                builder.newComponent("IfAny")
                                                                        .addComponent(
                                                                                builder.newComponent("IfLastModified")
                                                                                        .addAttribute("age", FileInfoParams.daysKept)
                                                                        )
                                                                        .addComponent(
                                                                                builder.newComponent("IfAccumulatedFileSize")
                                                                                        .addAttribute("exceeds", FileInfoParams.sizeKept)
                                                                        )
                                                                        .addComponent(
                                                                                builder.newComponent("IfAccumulatedFileCount")
                                                                                        .addAttribute("exceeds", FileInfoParams.countKept)
                                                                        )
                                                        )
                                        )
                        );

        // <Appender>
        return builder.newAppender("file-info", "RollingFile")
                .addAttribute("fileName", dirPath + "info/" + "info.log")
                .addAttribute("filePattern", dirPath + "info/" + filePattern)
                .addComponent(fileLayout)
                .addComponent(levelMatchFilter)
                .addComponent(policies)
                .addComponent(RolloverStrategy);
    }

    /**
     * 输出到Kafka的Appender组件配置
     */
    private AppenderComponentBuilder createKafkaAppender(ConfigurationBuilder<BuiltConfiguration> builder) {
        // <Layout>
        LayoutComponentBuilder kafkaLayout =
                builder.newLayout("PatternLayout")
                        .addAttribute("pattern", dataPattern);

        // <Filter>
        FilterComponentBuilder levelMatchFilter =
                builder.newFilter("LevelMatchFilter", Filter.Result.NEUTRAL, Filter.Result.DENY)
                        .addAttribute("level", "BIGDATA");

        return builder.newAppender("big-data", "Kafka")
                .addAttribute("topic", BigDataParams.topic)
                .addAttribute("key", BigDataParams.key)
                .addAttribute("syncSend", BigDataParams.syncSend)
                .addComponent(kafkaLayout)
                .addComponent(levelMatchFilter)
                .addComponent(builder.newProperty("bootstrap.servers", BigDataParams.bootstrapServers))
                .addComponent(builder.newProperty("buffer.memory", String.valueOf(BigDataParams.bufferMemory)))
                .addComponent(builder.newProperty("batch.size", String.valueOf(BigDataParams.batchSize)))
                .addComponent(builder.newProperty("linger.ms", String.valueOf(BigDataParams.lingerMs)))
                .addComponent(builder.newProperty("acks", String.valueOf(BigDataParams.acks)))
                .addComponent(builder.newProperty("retries", String.valueOf(BigDataParams.retries)))
                .addComponent(builder.newProperty("request.timeout.ms", String.valueOf(BigDataParams.requestTimeoutMS)))
                .addComponent(builder.newProperty("compression.type", BigDataParams.compressionType.name));
    }
}
