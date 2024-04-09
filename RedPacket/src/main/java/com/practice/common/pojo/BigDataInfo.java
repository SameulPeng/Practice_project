package com.practice.common.pojo;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.annotation.JSONType;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * 红包行为信息类
 */
@Getter
@Setter
@JSONType(orders = {"status", "key", "userId", "publishInfo", "shareInfo", "settleInfo", "errorType"})
public class BigDataInfo {
    private Status status; // 结果状态
    private String key; // 红包key
    private String userId; // 用户ID
    private Publish publishInfo; // 发起抢红包信息
    private Share shareInfo; // 参与抢红包信息
    private Settle settleInfo; // 结算信息
    private ErrorType errorType; // 错误类型

    private BigDataInfo() {}

    private BigDataInfo(Status status, String key, String userId, Publish publishInfo, Share shareInfo, Settle settleInfo, ErrorType errorType) {
        this.status = status;
        this.key = key;
        this.userId = userId;
        this.publishInfo = publishInfo;
        this.shareInfo = shareInfo;
        this.settleInfo = settleInfo;
        this.errorType = errorType;
    }

    public static BigDataInfo of(Status status, String key, String userId, Publish publishInfo, Share shareInfo, Settle settleInfo, ErrorType errorType) {
        return new BigDataInfo(status, key, userId, publishInfo, shareInfo, settleInfo, errorType);
    }

    /**
     * 将红包行为信息转换为JSON格式字符串
     * @return JSON格式字符串
     */
    public String toJson() {
        return JSON.toJSONString(this);
    }

    /**
     * 将JSON格式字符串转换为红包行为信息
     * @param json JSON格式字符串
     * @return 红包行为信息
     */
    public static BigDataInfo toObject(String json) {
        return JSON.parseObject(json, BigDataInfo.class);
    }

    /**
     * 将红包行为信息编码为字符串
     * @return 编码字符串
     */
    public String encode() {
        // 格式为： 结果状态（1字节） + 英文逗号（1字节） + 红包key + 英文逗号（1字节） + 用户ID + 英文逗号（1字节） + 发起抢红包信息 / 参与抢红包信息 / 错误类型 / 结算信息

        // 将红包行为信息编码为4个字节数组
        byte[] b = new byte[] {(byte) (status.ordinal() & 0xFF)};
        byte[] bs1 = key != null ? key.getBytes(StandardCharsets.UTF_8) : new byte[] {};
        byte[] bs2 = userId != null ? userId.getBytes(StandardCharsets.UTF_8) : new byte[] {};
        byte[] bs3 = switch (status) {
            case ERROR -> i2bytes(errorType.code, 1);
            case PUBLISH -> publishInfo.encode();
            case SHARE -> shareInfo.encode();
            case SETTLE -> settleInfo.encode();
        };

        // 将4个字节数组拼接成字符串
        return new String(b, StandardCharsets.ISO_8859_1) + "," +
                new String(bs1, StandardCharsets.UTF_8) + "," +
                new String(bs2, StandardCharsets.UTF_8) + "," +
                new String(bs3, StandardCharsets.ISO_8859_1);
    }

    /**
     * 将编码字符串解码为红包行为信息
     * @param encodedString 编码字符串
     * @return 红包行为信息
     */
    @SuppressWarnings("unchecked")
    public static BigDataInfo decode(String encodedString) {
        // 格式为： 结果状态（1字节） + 英文逗号（1字节） + 红包key + 英文逗号（1字节） + 用户ID + 英文逗号（1字节） + 发起抢红包信息 / 参与抢红包信息 / 错误类型

        // 将字符串分割成四个子字符串
        String[] strs = encodedString.split(",", 4);

        // 解码并封装红包行为信息
        Status status = Status.forOrdinal((int) bytes2i(strs[0].getBytes(StandardCharsets.ISO_8859_1)));
        byte[] infoBytes = strs[3].getBytes(StandardCharsets.ISO_8859_1);
        return new BigDataInfo(
                status,
                strs[1].length() > 0 ? strs[1] : null,
                strs[2].length() > 0 ? strs[2] : null,
                status == Status.PUBLISH ? Publish.decode(infoBytes) : null,
                status == Status.SHARE ? Share.decode(infoBytes) : null,
                status == Status.SETTLE ? Settle.decode(infoBytes) : null,
                status == Status.ERROR ? ErrorType.forCode((int) bytes2i(infoBytes)) : null
        );
    }

    /**
     * 将整型数转换为字节数组
     * @param i 整型数
     * @param byteNum 字节数
     * @return 字节数组
     */
    public static byte[] i2bytes(long i, int byteNum) {
        byte[] bytes = new byte[byteNum];
        for (int k = 0; k < byteNum; k++) {
            // 截取整型数i二进制表示的第(k + 1)个低8位
            bytes[k] = (byte) (i >>> (k << 3) & 0xFF);
        }
        return bytes;
    }

    /**
     * 将字节数组转换为整型数
     * @param bytes 字节数组
     * @return 整型数
     */
    public static long bytes2i(byte[] bytes) {
        long l = 0L;
        for (int k = 0; k < bytes.length; k++) {
            // 填充整型数l二进制表示的第(k + 1)个低8位
            l ^= ((long) (bytes[k] & 0xFF) << (k << 3));
        }
        return l;
    }

    /**
     * 结果状态
     */
    public enum Status {
        /**
         * 响应错误
         */
        ERROR,
        /**
         * 发起抢红包成功
         */
        PUBLISH,
        /**
         * 参与抢红包成功
         */
        SHARE,
        /**
         * 红包结算
         */
        SETTLE;

        /**
         * 根据枚举值序号返回结果状态
         * @param ordinal 枚举值序号
         * @return 结果状态
         */
        public static Status forOrdinal(int ordinal) {
            return switch (ordinal) {
                case 0 -> ERROR;
                case 1 -> PUBLISH;
                case 2 -> SHARE;
                case 3 -> SETTLE;
                default -> throw new IllegalStateException("Unexpected Status: " + ordinal);
            };
        }
    }

    /**
     * 发起抢红包信息类
     */
    @Getter
    @Setter
    @ToString
    @EqualsAndHashCode
    @JSONType(orders = {"amount", "shareNum", "expireTime", "timestamp"})
    public static class Publish {
        private int amount; // 红包总金额
        private int shareNum; // 红包份数
        private int expireTime; // 红包有效期
        private long timestamp; // 发起抢红包毫秒时间戳

        private Publish() {}

        private Publish(int amount, int shareNum, int expireTime, long timestamp) {
            this.amount = amount;
            this.shareNum = shareNum;
            this.expireTime = expireTime;
            this.timestamp = timestamp;
        }

        public static Publish of(int amount, int shareNum, int expireTime, long timestamp) {
            return new Publish(amount, shareNum, expireTime, timestamp);
        }

        /**
         * 将发起抢红包信息编码为字节数组
         * @return 字节数组
         */
        public byte[] encode() {
            // 格式为： 红包总金额（4字节） + 红包份数（2字节） + 红包有效期（3字节） + 发起抢红包毫秒时间戳（6字节）

            // 将发起抢红包信息编码为4个字节数组
            byte[] bs1 = i2bytes(amount, 4);
            byte[] bs2 = i2bytes(shareNum, 2);
            byte[] bs3 = i2bytes(expireTime, 3);
            byte[] bs4 = i2bytes(timestamp, 6);

            // 将4个字节数组拼接为一个字节数组
            byte[] bytes = new byte[bs1.length + bs2.length + bs3.length + bs4.length];
            List<byte[]> list = Arrays.asList(bs1, bs2, bs3, bs4);
            int len = 0;
            for (byte[] bs : list) {
                System.arraycopy(bs, 0, bytes, len, bs.length);
                len += bs.length;
            }
            return bytes;
        }

        /**
         * 将字节数组解码为发起抢红包信息
         * @param encoded 字节数组
         * @return 发起抢红包信息
         */
        public static Publish decode(byte[] encoded) {
            // 格式为： 红包总金额（4字节） + 红包份数（2字节） + 红包有效期（3字节） + 红包发起毫秒时间戳（6字节）

            // 解码字节数组，封装发起抢红包信息
            int n1 = 4, n2 = 2, n3 = 3, n4 = 6;
            return new Publish(
                    (int) bytes2i(Arrays.copyOfRange(encoded, 0, n1)),
                    (int) bytes2i(Arrays.copyOfRange(encoded, n1, n1 + n2)),
                    (int) bytes2i(Arrays.copyOfRange(encoded, n1 + n2, n1 + n2 + n3)),
                    bytes2i(Arrays.copyOfRange(encoded, n1 + n2 + n3, n1 + n2 + n3 + n4))
            );
        }
    }

    /**
     * 参与抢红包信息类
     */
    @Getter
    @Setter
    @ToString
    @EqualsAndHashCode
    @JSONType(orders = {"shareType", "share", "timeCost", "timestamp"})
    public static class Share {
        private ShareType shareType; // 参与抢红包结果类型
        private int share; // 抢到的红包金额，要求非负
        private long timeCost; // 抢到红包的耗时，要求非负
        private long timestamp; // 参与抢红包毫秒时间戳

        private Share() {}

        private Share(ShareType shareType, int share, long timeCost, long timestamp) {
            this.shareType = shareType;
            this.share = share;
            this.timeCost = timeCost;
            this.timestamp = timestamp;
        }

        public static Share of(ShareType shareType, int share, long timeCost, long timestamp) {
            return new Share(shareType, share, timeCost, timestamp);
        }

        /**
         * 将参与抢红包信息编码为字节数组
         * @return 字节数组
         */
        public byte[] encode() {
            // 格式为： 参与抢红包结果类型（1字节） + 抢到的红包金额（4字节） + 抢到红包的耗时（5字节） + 参与抢红包毫秒时间戳（6字节）

            // 将参与抢红包信息编码为4个字节数组
            byte[] bs1 = i2bytes(shareType.ordinal(), 1);
            byte[] bs2 = i2bytes(share, 4);
            byte[] bs3 = i2bytes(timeCost, 5);
            byte[] bs4 = i2bytes(timestamp, 6);

            // 将4个字节数组拼接为一个字节数组
            byte[] bytes = new byte[bs1.length + bs2.length + bs3.length + bs4.length];
            List<byte[]> list = Arrays.asList(bs1, bs2, bs3, bs4);
            int len = 0;
            for (byte[] bs : list) {
                System.arraycopy(bs, 0, bytes, len, bs.length);
                len += bs.length;
            }
            return bytes;
        }

        /**
         * 将字节数组解码为参与抢红包信息
         * @param encoded 字节数组
         * @return 发起抢红包信息
         */
        public static Share decode(byte[] encoded) {
            // 格式为： 参与抢红包结果类型（1字节） + 抢到的红包金额（4字节） + 抢到红包的耗时（5字节） + 参与抢红包毫秒时间戳（6字节）

            // 解码字节数组，封装参与抢红包信息
            int n1 = 1, n2 = 4, n3 = 5, n4 = 6;
            return new Share(
                    ShareType.forOrdinal((int) bytes2i(Arrays.copyOfRange(encoded, 0, n1))),
                    (int) bytes2i(Arrays.copyOfRange(encoded, n1, n1 + n2)),
                    bytes2i(Arrays.copyOfRange(encoded, n1 + n2, n1 + n2 + n3)),
                    bytes2i(Arrays.copyOfRange(encoded, n1 + n2 + n3, n1 + n2 + n3 + n4))
            );
        }

        /**
         * 参与抢红包结果类型
         */
        public enum ShareType {
            /**
             * 抢红包未结束，抢到红包
             */
            SUCCESS_ONGOING,
            /**
             * 抢红包已结束，但已经参与过并抢到红包
             */
            SUCCESS_END,
            /**
             * 抢红包已结束，抢不到红包
             */
            FAIL_END,
            /**
             * 抢红包未结束，已经参与过并抢到红包，重复参与
             */
            FAIL_REDO,
            /**
             * 查询过早的红包，结果已经过期
             */
            FAIL_NOT_FOUND;

            public static ShareType forOrdinal(int ordinal) {
                return switch (ordinal) {
                    case 0 -> SUCCESS_ONGOING;
                    case 1 -> SUCCESS_END;
                    case 2 -> FAIL_END;
                    case 3 -> FAIL_REDO;
                    case 4 -> FAIL_NOT_FOUND;
                    default -> throw new IllegalStateException("Unexpected ShareType: " + ordinal);
                };
            }
        }
    }

    /**
     * 结算信息类
     */
    @Getter
    @Setter
    @ToString
    @EqualsAndHashCode
    @JSONType(orders = {"finished", "participantNum", "maxShare", "minShare", "shortestTimeCost", "longestTimeCost"})
    public static class Settle {
        private boolean finished; // 是否被抢完
        private int participantNum; // 参与抢红包人数
        private int maxShare; // 抢到的最大红包金额
        private int minShare; // 抢到的最小红包金额
        private long shortestTimeCost; // 抢到红包的最短耗时
        private long longestTimeCost; // 抢到红包的最长耗时
        private long timestamp; // 红包结算毫秒时间戳

        private Settle() {}

        private Settle(boolean finished, int participantNum, int maxShare, int minShare, long shortestTimeCost, long longestTimeCost, long timestamp) {
            this.finished = finished;
            this.participantNum = participantNum;
            this.maxShare = maxShare;
            this.minShare = minShare;
            this.shortestTimeCost = shortestTimeCost;
            this.longestTimeCost = longestTimeCost;
            this.timestamp = timestamp;
        }

        public static Settle of(Map<String, ShareInfo> map, int amount, long timestamp) {
            int shared = 0, participantNum = 0, maxShare = 0, minShare = 0;
            long shortestTimeCost = 0, longestTimeCost = 0;
            // 遍历解析后的红包结果，统计红包是否被抢完、参与抢红包人数、抢到的最大/最小红包金额、抢到红包的最短/最长耗时
            for (ShareInfo info : map.values()) {
                int share = info.getShare();
                long timeCost = info.getTimeCost();
                shared += share;
                participantNum++;
                maxShare = Math.max(share, maxShare);
                minShare = minShare == 0 ? share : Math.min(share, minShare);
                shortestTimeCost = shortestTimeCost == 0 ? timeCost : Math.min(timeCost, shortestTimeCost);
                longestTimeCost = Math.max(timeCost, longestTimeCost);
            }
            return new Settle(shared >= amount, participantNum, maxShare, minShare, shortestTimeCost, longestTimeCost, timestamp);
        }

        /**
         * 将结算信息编码为字节数组
         * @return 字节数组
         */
        public byte[] encode() {
            // 格式为： 是否被抢完（1字节） + 参与抢红包人数（2字节） + 抢到的最大红包金额（4字节） + 抢到的最小红包金额（4字节） +
            //          抢到红包的最短耗时（5字节） + 抢到红包的最长耗时（5字节） + 红包结算毫秒时间戳（6字节）

            // 将结算信息编码为7个字节数组
            byte[] bs1 = i2bytes(finished ? 1 : 0, 1);
            byte[] bs2 = i2bytes(participantNum, 2);
            byte[] bs3 = i2bytes(maxShare, 4);
            byte[] bs4 = i2bytes(minShare, 4);
            byte[] bs5 = i2bytes(shortestTimeCost, 5);
            byte[] bs6 = i2bytes(longestTimeCost, 5);
            byte[] bs7 = i2bytes(timestamp, 6);

            // 将7个字节数组拼接为一个字节数组
            byte[] bytes = new byte[bs1.length + bs2.length + bs3.length + bs4.length + bs5.length + bs6.length + bs7.length];
            List<byte[]> list = Arrays.asList(bs1, bs2, bs3, bs4, bs5, bs6, bs7);
            int len = 0;
            for (byte[] bs : list) {
                System.arraycopy(bs, 0, bytes, len, bs.length);
                len += bs.length;
            }
            return bytes;
        }

        /**
         * 将字节数组解码为结算信息
         * @param encoded 字节数组
         * @return 结算信息
         */
        public static Settle decode(byte[] encoded) {
            // 格式为： 是否被抢完（1字节） + 参与抢红包人数（2字节） + 抢到的最大红包金额（4字节） + 抢到的最小红包金额（4字节） +
            //          抢到红包的最短耗时（5字节） + 抢到红包的最长耗时（5字节） + 红包结算毫秒时间戳（6字节）

            // 解码字节数组，封装结算信息
            int[] lengths = {1, 2, 4, 4, 5, 5, 6};
            int len = 0;
            byte[][] bytes = new byte[7][];
            for (int i = 0; i < 7; i++) {
                bytes[i] = Arrays.copyOfRange(encoded, len, len += lengths[i]);
            }
            return new Settle(
                    ((int) bytes2i(bytes[0])) == 1,
                    (int) bytes2i(bytes[1]),
                    (int) bytes2i(bytes[2]),
                    (int) bytes2i(bytes[3]),
                    bytes2i(bytes[4]),
                    bytes2i(bytes[5]),
                    bytes2i(bytes[6])
            );
        }
    }

    /**
     * 错误类型
     */
    public enum ErrorType {
        /**
         * 未知错误
         */
        UNKNOWN_ERROR(0),
        /**
         * 余额不足
         */
        BALANCE_NOT_ENOUGH(1),
        /**
         * 账户异常
         */
        ILLEGAL_ACCOUNT(2),
        /**
         * 总金额设置超出范围
         */
        WRONG_AMOUNT(3),
        /**
         * 份数设置超出范围
         */
        WRONG_SHARE_NUM(4),
        /**
         * 有效期设置超出范围
         */
        WRONG_EXPIRE_TIME(5),
        /**
         * 参与抢红包无法获得有效结果
         */
        SHARE_ERROR(6),
        /**
         * 用户不匹配
         */
        USER_MISMATCH(7);

        private final Integer code;

        ErrorType(Integer code) {
            this.code = code;
        }

        /**
         * 根据错误码返回错误类型
         * @param code 错误码
         * @return 错误类型
         */
        public static ErrorType forCode(int code) {
            return switch (code) {
                case 0 -> UNKNOWN_ERROR;
                case 1 -> BALANCE_NOT_ENOUGH;
                case 2 -> ILLEGAL_ACCOUNT;
                case 3 -> WRONG_AMOUNT;
                case 4 -> WRONG_SHARE_NUM;
                case 5 -> WRONG_EXPIRE_TIME;
                case 6 -> SHARE_ERROR;
                case 7 -> USER_MISMATCH;
                default -> throw new IllegalStateException("Unexpected ErrorType: " + code);
            };
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BigDataInfo that)) return false;

        if (status != that.status) return false;
        if (!Objects.equals(key, that.key)) return false;
        if (!Objects.equals(userId, that.userId)) return false;
        if (!Objects.equals(publishInfo, that.publishInfo)) return false;
        if (!Objects.equals(shareInfo, that.shareInfo)) return false;
        if (!Objects.equals(settleInfo, that.settleInfo)) return false;
        return errorType == that.errorType;
    }

    @Override
    public int hashCode() {
        int result = status.hashCode();
        result = 31 * result + (key != null ? key.hashCode() : 0);
        result = 31 * result + (userId != null ? userId.hashCode() : 0);
        result = 31 * result + (publishInfo != null ? publishInfo.hashCode() : 0);
        result = 31 * result + (shareInfo != null ? shareInfo.hashCode() : 0);
        result = 31 * result + (settleInfo != null ? settleInfo.hashCode() : 0);
        result = 31 * result + (errorType != null ? errorType.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return this.toJson();
    }
}
