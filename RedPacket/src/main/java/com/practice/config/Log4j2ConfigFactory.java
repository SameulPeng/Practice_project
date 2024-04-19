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
import org.springframework.util.StringUtils;

import java.io.BufferedReader;
import java.io.FileReader;
import java.net.URI;
import java.util.Properties;

/**
 *  日志配置工厂类<br/>
 *  生效条件<br/>
 *  1. 在 application.properties 配置文件中设置配置文件路径 logging.config=...<br/>
 *  2. 创建上述路径指定的拓展名为 cfg 的配置文件<br/>
 *  覆盖条件<br/>
 *  1. 更改或移除配置文件路径 logging.config=...<br/>
 *  2. 在上述路径或 resources 目录下创建 xml、json、yaml、properties 等配置文件
 */
/*
    原理
    由ConfigurationFactory类的静态内部类Factory的getConfiguration(LoggerContext, ConfigurationSource)方法确定配置工厂类
        如果没有设置配置文件路径，则按优先级从高到低遍历配置工厂类，找到第一个支持类型包含全类型"*"的配置工厂类
        如果设置了配置文件路径，则按优先级从高到低遍历配置工厂类，找到第一个支持类型包含全类型"*"或配置文件扩展名的配置工厂类
        默认配置工厂类
            当前自定义 优先级 9 支持类型 cfg
            Properties 优先级 8 支持类型 properties
            Yaml 优先级 7 支持类型 yaml yml
            JSON 优先级 6 支持类型 json jsn
            XML 优先级 5 支持类型 xml *
            SpringBoot 优先级 0 支持类型 .springboot
    因此当前自定义配置工厂类在设置了配置项 logging.config 为拓展名 cfg 的配置文件时生效，并从中读取配置项值，覆盖默认值
 */
@Plugin(name = "Log4j2ConfigFactory", category = ConfigurationFactory.CATEGORY)
// 指定配置工厂类的优先级，数值越大，优先级越高
// 原有配置工厂类的优先级，SpringBoot自动配置优先级为0，xml、json、yaml、properties等文件配置优先级分别为5、6、7、8
@Order(9)
public class Log4j2ConfigFactory extends ConfigurationFactory {
    /**
     * 日志格式
     */
    private String logPattern = "%d{yyyy-MM-dd HH:mm:ss.SSS} %highlight{%-5.5p} [%-20.20t] %-60.60c{1.}:   %m%n";
    /**
     * 大数据格式
     */
    private String dataPattern = "%m";
    /**
     * 日志文件目录
     */
    private String dirPath = "./logs/";
    /**
     * 日志文件名称格式
     */
    private String filePattern = "%d{yyyy-MM-dd}/%d{yyyy-MM-dd-HH}_%i.log.gz";
    /**
     * 是否输出日志到控制台
     */
    private boolean toConsole = true;
    /**
     * 是否输出日志到文件
     */
    private boolean toFile = false;
    /**
     * 是否输出日志到大数据接口
     */
    private boolean toBigData = false;

    private void applyConfig(String location) {
        // 读取配置文件
        Properties params = new Properties();
        try (BufferedReader br = new BufferedReader(new FileReader(location))) {
            params.load(br);
        } catch (Exception ignore) {}

        // 使用读取到的有效配置项值覆盖默认值
        String value;
        if (params.size() > 0) {
            if (StringUtils.hasLength(value = (String) params.get("log-pattern"))) this.logPattern = value;
            if (StringUtils.hasLength(value = (String) params.get("data-pattern"))) this.dataPattern = value;
            if (StringUtils.hasLength(value = (String) params.get("dir-path"))) this.dirPath = value;
            if (StringUtils.hasLength(value = (String) params.get("file-pattern"))) this.filePattern = value;
            if (StringUtils.hasLength(value = (String) params.get("to-console"))) this.toConsole = Boolean.parseBoolean(value);
            if (StringUtils.hasLength(value = (String) params.get("to-file"))) this.toFile = Boolean.parseBoolean(value);
            if (StringUtils.hasLength(value = (String) params.get("to-big-data"))) this.toBigData = Boolean.parseBoolean(value);

            if (StringUtils.hasLength(value = (String) params.get("file-warn.active"))) FileWarnParams.active = Boolean.parseBoolean(value);
            if (StringUtils.hasLength(value = (String) params.get("file-warn.size-each-file"))) FileWarnParams.sizeEachFile = value;
            if (StringUtils.hasLength(value = (String) params.get("file-warn.count-each-day"))) FileWarnParams.countEachDay = Integer.parseInt(value);
            if (StringUtils.hasLength(value = (String) params.get("file-warn.days-kept"))) FileWarnParams.daysKept = value;
            if (StringUtils.hasLength(value = (String) params.get("file-warn.size-kept"))) FileWarnParams.sizeKept = value;
            if (StringUtils.hasLength(value = (String) params.get("file-warn.count-kept"))) FileWarnParams.countKept = Integer.parseInt(value);

            if (StringUtils.hasLength(value = (String) params.get("file-biz.active"))) FileBizParams.active = Boolean.parseBoolean(value);
            if (StringUtils.hasLength(value = (String) params.get("file-biz.size-each-file"))) FileBizParams.sizeEachFile = value;
            if (StringUtils.hasLength(value = (String) params.get("file-biz.days-kept"))) FileBizParams.daysKept = value;
            if (StringUtils.hasLength(value = (String) params.get("file-biz.size-kept"))) FileBizParams.sizeKept = value;
            if (StringUtils.hasLength(value = (String) params.get("file-biz.count-kept"))) FileBizParams.countKept = Integer.parseInt(value);

            if (StringUtils.hasLength(value = (String) params.get("file-info.active"))) FileInfoParams.active = Boolean.parseBoolean(value);
            if (StringUtils.hasLength(value = (String) params.get("file-info.size-each-file"))) FileInfoParams.sizeEachFile = value;
            if (StringUtils.hasLength(value = (String) params.get("file-info.count-each-day"))) FileInfoParams.countEachDay = Integer.parseInt(value);
            if (StringUtils.hasLength(value = (String) params.get("file-info.days-kept"))) FileInfoParams.daysKept = value;
            if (StringUtils.hasLength(value = (String) params.get("file-info.size-kept"))) FileInfoParams.sizeKept = value;
            if (StringUtils.hasLength(value = (String) params.get("file-info.count-kept"))) FileInfoParams.countKept = Integer.parseInt(value);

            if (StringUtils.hasLength(value = (String) params.get("kafka.bootstrap-servers"))) BigDataParams.bootstrapServers = value;
            if (StringUtils.hasLength(value = (String) params.get("kafka.topic"))) BigDataParams.topic = value;
            if (StringUtils.hasLength(value = (String) params.get("kafka.key"))) BigDataParams.key = value;
            if (StringUtils.hasLength(value = (String) params.get("kafka.sync-send"))) BigDataParams.syncSend = Boolean.parseBoolean(value);
            if (StringUtils.hasLength(value = (String) params.get("kafka.buffer-memory"))) BigDataParams.bufferMemory = Long.parseLong(value);
            if (StringUtils.hasLength(value = (String) params.get("kafka.batch-size"))) BigDataParams.batchSize = Long.parseLong(value);
            if (StringUtils.hasLength(value = (String) params.get("kafka.linger-ms"))) BigDataParams.lingerMs = Long.parseLong(value);
            if (StringUtils.hasLength(value = (String) params.get("kafka.acks"))) BigDataParams.acks = Integer.parseInt(value);
            if (StringUtils.hasLength(value = (String) params.get("kafka.retries"))) BigDataParams.retries = Integer.parseInt(value);
            if (StringUtils.hasLength(value = (String) params.get("kafka.request-timeout-ms"))) BigDataParams.requestTimeoutMS = Long.parseLong(value);
            if (StringUtils.hasLength(value = (String) params.get("kafka.compression-type"))) {
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
        /**
         * 是否输出到日志文件
         */
        private static boolean active = true;
        /**
         * 单个文件最大大小
         */
        private static String sizeEachFile = "200 MB";
        /**
         * 单日文件最大数量
         */
        private static int countEachDay = 10;
        /**
         * 文件保留天数
         */
        private static String daysKept = "7d";
        /**
         * 文件最大总大小
         */
        private static String sizeKept = "10 GB";
        /**
         * 文件最大总数量
         */
        private static int countKept = 100;
    }

    /**
     * 业务日志文件（BIZ日志级别）配置
     */
    public static class FileBizParams {
        /**
         * 是否输出到日志文件
         */
        private static boolean active = true;
        /**
         * 单个文件最大大小
         */
        private static String sizeEachFile = "200 MB";
        /**
         * 文件保留天数
         */
        private static String daysKept = "30d";
        /**
         * 文件最大总大小
         */
        private static String sizeKept = "100 GB";
        /**
         * 文件最大总数量
         */
        private static int countKept = 1000;
    }

    /**
     * 普通日志文件（INFO日志级别）配置
     */
    private static class FileInfoParams {
        /**
         * 是否输出到日志文件
         */
        private static boolean active = false;
        /**
         * 单个文件最大大小
         */
        private static String sizeEachFile = "200 MB";
        /**
         * 单日文件最大数量
         */
        private static int countEachDay = 10;
        /**
         * 文件保留天数
         */
        private static String daysKept = "7d";
        /**
         * 文件最大总大小
         */
        private static String sizeKept = "10 GB";
        /**
         * 文件最大总数量
         */
        private static int countKept = 100;
    }

    /**
     * 大数据接口（BIGDATA日志级别）配置
     */
    private static class BigDataParams {
        /**
         * Kafka 服务器地址
         */
        private static String bootstrapServers = "localhost:9092";
        /**
         * Kafka 主题
         */
        private static String topic = "red-packet-big-data";
        /**
         * Kafka 消息key
         */
        private static String key = null;
        /**
         * Kafka 是否同步发送
         */
        private static boolean syncSend = false;
        /**
         * Kafka 缓冲区大小
         */
        private static long bufferMemory = 64 << 10 << 10;
        /**
         * Kafka 缓冲区数据批次大小
         */
        private static long batchSize = 32 << 10;
        /**
         * Kafka 缓冲区数据拉取延迟时间
         */
        private static long lingerMs = 1000;
        /**
         * Kafka 生产应答方式
         */
        private static int acks = -1;
        /**
         * Kafka 重试次数
         */
        private static int retries = 3;
        /**
         * Kafka 请求超时时间
         */
        private static long requestTimeoutMS = 1000;
        /**
         * Kafka 压缩方式
         */
        private static CompressionType compressionType = CompressionType.NONE;
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
        applyConfig(source.getLocation());
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
                .setStatusLevel(Level.WARN)
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
            if (FileWarnParams.active) {
                builder.add(createFileWarnAppender(builder));
                rootLogger.add(builder.newAppenderRef("file-warn"));
            }
            if (FileBizParams.active) {
                builder.add(createFileBizAppender(builder));
                rootLogger.add(builder.newAppenderRef("file-biz"));
            }
            if (FileInfoParams.active) {
                builder.add(createFileInfoAppender(builder));
                rootLogger.add(builder.newAppenderRef("file-info"));
            }
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
