package com.practice.common.util;

import org.springframework.boot.system.ApplicationHome;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;

/**
 * 获取配置文件工具类
 */
public class ViableConfigUtil {
    /**
     * 默认搜索的路径集合<br/>
     * 按顺序查找所在目录下的config、cfg、conf目录路径和当前路径<br/>
     * 在任意一个路径下找到匹配文件名的文件后返回
     */
    private static final String[] alternateDirs = {
            "config", "cfg", "conf", ""
    };

    /**
     * 按照规则查找匹配文件名的文件
     * @param filename 文件名
     * @return 输入流对象
     */
    public static InputStream get(String filename) throws FileNotFoundException {
        File file;

        // 获取jar包所在目录路径
        String dir = new ApplicationHome().getDir().getPath();

        // 在jar包外部路径集合搜索
        for (String p : alternateDirs) {
            file = new File(dir + p + File.separatorChar + filename);
            if (file.exists()) return new FileInputStream(file);
        }

        // 在jar包内部路径集合搜索
        for (String p : alternateDirs) {
            InputStream is = ViableConfigUtil.class.getClassLoader().getResourceAsStream(p + '/' + filename);
            if (is != null) return is;
        }

        throw new FileNotFoundException(
                "未找到指定配置文件，请确认jar包所在目录路径或jar包类路径下的以下路径下是否存在该文件：./config/ ./cfg/ ./conf/ ./"
        );
    }
}
