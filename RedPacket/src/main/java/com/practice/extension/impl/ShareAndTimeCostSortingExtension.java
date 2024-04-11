package com.practice.extension.impl;

import com.practice.common.pojo.ShareInfo;
import com.practice.common.pojo.SortedShareInfo;
import com.practice.config.RedPacketProperties;
import com.practice.extension.RedPacketExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.*;

@Component
@Profile("biz")
public class ShareAndTimeCostSortingExtension implements RedPacketExtension {
    private int shareRankNum;
    private int timeCostRankNum;
    private RedPacketProperties redPacketProperties;

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
        RepeatableBinarySearchTree<Integer> shareRank = new RepeatableBinarySearchTree<>(shareRankNum, true);
        RepeatableBinarySearchTree<Long> timeCostRank = new RepeatableBinarySearchTree<>(timeCostRankNum, false);

        for (Map.Entry<String, Object> entry : mapResult.entrySet()) {
            String userId = entry.getKey();
            ShareInfo shareInfo = (ShareInfo) entry.getValue();
            SortedShareInfo sortedShareInfo = new SortedShareInfo(shareInfo, -1, -1);
            mapResult.put(userId, sortedShareInfo);
            shareRank.put(userId, shareInfo.getShare());
            timeCostRank.put(userId, shareInfo.getTimeCost());
        }

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

    private static class RepeatableBinarySearchTree<T extends Comparable<T>> {
        private Node root = null;
        private Node leftmost = null;
        private Node rightmost = null;
        private int size = 0;
        private final int capacity;
        private final boolean saveLarger;

        private RepeatableBinarySearchTree(int capacity, boolean saveLarger) {
            if (capacity < 1) throw new IllegalArgumentException("capacity should be greater than 0");
            this.capacity = capacity;
            this.saveLarger = saveLarger;
        }

        private void put(String k, T v) {
            if (size == 0) {
                leftmost = rightmost = root = new Node(k, v);
                size++;
            } else {
                if (size < capacity) {
                    add(k, v);
                    size++;
                } else if ((saveLarger && v.compareTo(leftmost.value) > 0)
                        || (!saveLarger && v.compareTo(rightmost.value) < 0)) {
                    add(k, v);
                    remove(saveLarger ? leftmost : rightmost);
                }
            }
        }

        private void add(String k, T v) {
            if (root == null) throw new NullPointerException("root is null");

            Node n = root, father = root;
            boolean left = true;
            while (n != null) {
                father = n;
                int compare = v.compareTo(n.value);
                left = compare < 0 || (saveLarger && compare == 0);
                n = left ? n.left : n.right;
            }

            Node node = new Node(k, v);
            if (left) {
                father.left = node;
                if (father == leftmost) leftmost = node;
            } else {
                father.right = node;
                node.isLeftChild = false;
                if (father == rightmost) rightmost = node;
            }
            node.father = father;
        }

        private void remove(Node node) {
            if (node.left == null && node.right == null) {
                if (node == root) {
                    leftmost = rightmost = root = null;
                } else if (node.isLeftChild) {
                    node.father.left = null;
                    if (node == leftmost) leftmost = node.father;
                } else {
                    node.father.right = null;
                    if (node == rightmost) rightmost = node.father;
                }
            } else if (node.left != null && node.right == null) {
                if (node == root) {
                    rightmost = root = node.left;
                } else if (node.isLeftChild) {
                    node.father.left = node.left;
                } else {
                    node.father.right = node.left;
                    if (node == rightmost) rightmost = node.rightmost();
                }
            } else if (node.left == null) {
                if (node == root) {
                    leftmost = root = node.right;
                } else if (node.isLeftChild) {
                    node.father.left = node.right;
                    if (node == leftmost) leftmost = node.leftmost();
                } else {
                    node.father.right = node.right;
                }
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

        private List<String> list() {
            if (root == null) return Collections.emptyList();

            List<String> list = new ArrayList<>(size);
            Deque<Node> deque = new ArrayDeque<>(size);

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

        private class Node {
            private Node father = null;
            private boolean isLeftChild = true;
            private Node left = null;
            private Node right = null;
            private final String key;
            private final T value;

            private Node(String key, T value) {
                this.key = key;
                this.value = value;
            }

            private Node leftmost() {
                return left == null ? this : left.leftmost();
            }

            private Node rightmost() {
                return right == null ? this : right.rightmost();
            }
        }
    }
}
