package com.practice.extension.impl;

import com.practice.common.annotation.ExtensionPriority;
import com.practice.common.pojo.ShareInfo;
import com.practice.common.pojo.SortedShareInfo;
import com.practice.config.RedPacketProperties;
import com.practice.extension.RedPacketExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.*;

/**
 * 抢红包金额耗时排名扩展类<br/>
 * 为每个红包中抢到红包的用户，分别按照抢到的金额和耗时进行降序和升序排名<br/>
 * 仅排序前N名，N的值通过配置项 red-packet.biz.share-rank-num 和 red-packet.biz.time-cost-rank-num 配置
 */
@Component
@Profile("biz")
@ExtensionPriority(10)
public class ShareAndTimeCostSortingExtension implements RedPacketExtension {
    private int shareRankNum; // 参与抢红包金额排名数量，即显示抢红包金额最大的前若干名
    private int timeCostRankNum; // 参与抢红包耗时排名数量，即显示抢红包耗时最短的前若干名
    private RedPacketProperties redPacketProperties; // 配置参数类

    @Autowired
    private void setRedPacketProperties(RedPacketProperties redPacketProperties) {
        this.redPacketProperties = redPacketProperties;
    }

    @PostConstruct
    private void init() {
        shareRankNum = redPacketProperties.getBiz().getShareRankNum();
        timeCostRankNum = redPacketProperties.getBiz().getTimeCostRankNum();
    }

    @Override
    public Map<String, Object> onCache(Map<String, Object> mapResult) {
        // 根据排名数量，创建二叉搜索树
        RepeatableBinarySearchTree<Integer> shareRank = new RepeatableBinarySearchTree<>(shareRankNum, true);
        RepeatableBinarySearchTree<Long> timeCostRank = new RepeatableBinarySearchTree<>(timeCostRankNum, false);

        // 遍历红包结果，将红包结果每个用户项的信息封装类更新为包含排名的子类
        for (Map.Entry<String, Object> entry : mapResult.entrySet()) {
            String userId = entry.getKey();
            ShareInfo shareInfo = (ShareInfo) entry.getValue();

            SortedShareInfo sortedShareInfo = new SortedShareInfo(shareInfo);
            mapResult.put(userId, sortedShareInfo);

            // 通过二叉搜索树对抢到的红包金额和耗时进行有限数量的排序
            shareRank.put(userId, shareInfo.getShare());
            timeCostRank.put(userId, shareInfo.getTimeCost());
        }

        // 从二叉搜索树获取排序结果，更新红包结果中进入排名的用户项的排名信息
        List<String> shareRankList = shareRank.list();
        for (int i = 0; i < shareRankList.size(); i++) {
            ((SortedShareInfo) mapResult.get(shareRankList.get(i))).setShareRank(i);
        }
        List<String> timeCostRankList = timeCostRank.list();
        for (int i = 0; i < timeCostRankList.size(); i++) {
            ((SortedShareInfo) mapResult.get(timeCostRankList.get(i))).setTimeCostRank(i);
        }

        return mapResult;
    }

    /**
     * 自定义二叉搜索树结构<br/>
     * 1. 允许相等的比较值<br/>
     * 2. 有限容量，达到容量上限后，新加入节点会淘汰一个已有节点<br/>
     * 3. 不具有自平衡特性，最差情况下退化成链表，但由于容量较小，性能影响不大
     * @param <T> 排序类型
     */
    private static class RepeatableBinarySearchTree<T extends Comparable<T>> {
        /**
         * 根节点
         */
        private Node root = null;
        /**
         * 最左节点
         */
        private Node leftmost = null;
        /**
         * 最右节点
         */
        private Node rightmost = null;
        /**
         * 当前节点数量
         */
        private int size = 0;
        /**
         * 节点数量上限
         */
        private final int capacity;
        /**
         * 是否保留最大一批的比较值<br/>
         * 如果为真，表示达到容量上限后，新加入节点的比较值必须大于已有节点中的最小值，同时将该节点淘汰<br/>
         * 如果为假，表示达到容量上限后，新加入节点的比较值必须小于已有节点中的最大值，同时将该节点淘汰
         */
        private final boolean saveLarger;

        private RepeatableBinarySearchTree(int capacity, boolean saveLarger) {
            if (capacity < 1) throw new IllegalArgumentException("capacity should be greater than 0");
            this.capacity = capacity;
            this.saveLarger = saveLarger;
        }

        /**
         * 尝试将节点加入二叉搜索树
         * @param k 标识值
         * @param v 比较值
         */
        private void put(String k, T v) {
            // 如果当前节点数量为0，则直接创建为根节点
            if (size == 0) {
                leftmost = rightmost = root = new Node(k, v);
                size++;
            } else {
                // 如果当前节点数量未达到上限，则直接加入
                if (size < capacity) {
                    add(k, v);
                    size++;
                // 如果当前节点数量达到上限，则满足以下任一条件时可以加入，同时移除最值对应的已有节点
                // 当前二叉搜索树保留最大一批的比较值，并且待加入比较值大于已有节点的最小值
                // 当前二叉搜索树保留最小一批的比较值，并且待加入比较值小于已有节点的最大值
                } else if ((saveLarger && v.compareTo(leftmost.value) > 0)
                        || (!saveLarger && v.compareTo(rightmost.value) < 0)) {
                    add(k, v);
                    remove(saveLarger ? leftmost : rightmost);
                }
            }
        }

        /**
         * 将节点加入二叉搜索树
         * @param k 标识值
         * @param v 比较值
         */
        private void add(String k, T v) {
            if (root == null) throw new NullPointerException("root is null");

            // 基于二叉搜索树的查找方式，找到新加入节点的父节点，并判断新加入节点是左子节点还是右子节点
            Node n = root, father = root;
            boolean left = true;
            while (n != null) {
                father = n;
                int compare = v.compareTo(n.value);
                left = compare < 0 || (saveLarger && compare == 0);
                n = left ? n.left : n.right;
            }

            // 加入节点
            Node node = new Node(k, v);
            if (left) {
                father.left = node;
                // 如果当前节点是左子节点，且父节点是最左节点，则更新最左节点为当前节点
                if (father == leftmost) leftmost = node;
            } else {
                father.right = node;
                node.isLeftChild = false;
                // 如果当前节点是右子节点，且父节点是最右节点，则更新最右节点为当前节点
                if (father == rightmost) rightmost = node;
            }
            node.father = father;
        }

        /**
         * 从二叉搜索树移除节点
         * @param node 待移除节点
         */
        private void remove(Node node) {
            // 如果当前节点没有子节点，则直接移除
            if (node.left == null && node.right == null) {
                if (node == root) {
                    leftmost = rightmost = root = null;
                } else if (node.isLeftChild) {
                    node.father.left = null;
                    // 如果当前节点是最左节点，则更新最左节点为当前节点的父节点
                    if (node == leftmost) leftmost = node.father;
                } else {
                    node.father.right = null;
                    // 如果当前节点是最右节点，则更新最右节点为当前节点的父节点
                    if (node == rightmost) rightmost = node.father;
                }
            // 如果当前节点只有左子节点，直接移除，并将当前节点的子树作为父节点的新子树
            } else if (node.left != null && node.right == null) {
                if (node == root) {
                    rightmost = root = node.left;
                } else if (node.isLeftChild) {
                    node.father.left = node.left;
                } else {
                    node.father.right = node.left;
                    // 如果当前节点是最右节点，则更新最右节点为以当前节点为根的子树的最右节点
                    if (node == rightmost) rightmost = node.rightmost();
                }
            // 如果当前节点只有右子节点，直接移除，并将当前节点的子树作为父节点的新子树
            } else if (node.left == null) {
                if (node == root) {
                    leftmost = root = node.right;
                } else if (node.isLeftChild) {
                    node.father.left = node.right;
                    // 如果当前节点是最左节点，则更新最左节点为以当前节点为根的子树的最左节点
                    if (node == leftmost) leftmost = node.leftmost();
                } else {
                    node.father.right = node.right;
                }
            // 如果当前节点有两个子节点，选择当前节点的左子树的最右节点或右子树的最左节点补占当前节点的位置，尽量避免倾斜
            // 最左节点和最右节点不会改变
            } else {
                Node n;
                if (node == root) {
                    n = saveLarger ? node.right.leftmost() : node.left.rightmost();
                    root = n;
                } else if (node.isLeftChild) {
                    n = node.right.leftmost();
                    node.father.left = n;
                } else {
                    n = node.left.rightmost();
                    node.father.right = n;
                }
                if (n.right != null) {
                    n.right.father = n.father;
                    n.father.left = n.right;
                }
                if (n.left != null) {
                    n.left.father = n.father;
                    n.father.right = n.left;
                }
                node.left.father = node.right.father = n;
                n.father = node.father;
                n.left = node.left;
                n.right = node.right;
            }
        }

        /**
         * 遍历二叉搜索树，获取按照对应比较值排序的标识值列表
         * @return 标识值列表
         */
        private List<String> list() {
            if (root == null) return Collections.emptyList();

            List<String> list = new ArrayList<>(size);
            Deque<Node> deque = new ArrayDeque<>(size);

            // 二叉搜索树的中序遍历，如果当前二叉搜索树保留最小一批的比较值，则进行正向的中序遍历，否则进行反向的中序遍历
            Node n = root;
            while (n != null || !deque.isEmpty()) {
                while (n != null) {
                    deque.addLast(n);
                    n = saveLarger ? n.right : n.left;
                }
                if (!deque.isEmpty()) {
                    n = deque.pollLast();
                    list.add(n.key);
                    n = saveLarger ? n.left : n.right;
                }
            }

            return list;
        }

        /**
         * 二叉搜索树的节点
         */
        private class Node {
            /**
             * 父节点
             */
            private Node father = null;
            /**
             * 当前节点是否是父节点的左子节点
             */
            private boolean isLeftChild = true;
            /**
             * 左子节点
             */
            private Node left = null;
            /**
             * 右子节点
             */
            private Node right = null;
            /**
             * 标识值
             */
            private final String key;
            /**
             * 比较值
             */
            private final T value;

            private Node(String key, T value) {
                this.key = key;
                this.value = value;
            }

            /**
             * 获取以当前节点为根的子树的最左节点
             * @return 以当前节点为根的子树的最左节点
             */
            private Node leftmost() {
                Node leftmost = this;
                for (Node n = left; n != null; n = n.left) leftmost = n;
                return leftmost;
            }

            /**
             * 获取以当前节点为根的子树的最右节点
             * @return 以当前节点为根的子树的最右节点
             */
            private Node rightmost() {
                Node rightmost = this;
                for (Node n = right; n != null; n = n.right) rightmost = n;
                return rightmost;
            }
        }
    }
}
