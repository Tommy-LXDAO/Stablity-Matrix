package com.stability.martrix.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Android Manifest.xml 解析工具类
 * 用于解析 AOSP 项目中的 manifest.xml 文件，根据 project 的 name 获取 revision 和 upstream
 */
public class ManifestParser {

    private static final Logger logger = LoggerFactory.getLogger(ManifestParser.class);

    /**
     * Manifest project 节点信息
     */
    public static class Project {
        private final String name;
        private final String path;
        private final String revision;
        private final String upstream;
        private final String remote;
        private final String groups;

        public Project(String name, String path, String revision, String upstream,
                       String remote, String groups) {
            this.name = name;
            this.path = path;
            this.revision = revision;
            this.upstream = upstream;
            this.remote = remote;
            this.groups = groups;
        }

        public String getName() {
            return name;
        }

        public String getPath() {
            return path;
        }

        public String getRevision() {
            return revision;
        }

        public String getUpstream() {
            return upstream;
        }

        public String getRemote() {
            return remote;
        }

        public String getGroups() {
            return groups;
        }

        @Override
        public String toString() {
            return "Project{name='" + name + "', path='" + path + "', revision='" + revision +
                    "', upstream='" + upstream + "'}";
        }
    }

    /**
     * 解析 manifest.xml 文件
     *
     * @param manifestFile manifest.xml 文件
     * @return Document 对象
     */
    public static Document parse(File manifestFile) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        return builder.parse(manifestFile);
    }

    /**
     * 解析 manifest.xml 文件（从 InputStream）
     *
     * @param inputStream manifest.xml 输入流
     * @return Document 对象
     */
    public static Document parse(InputStream inputStream) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        return builder.parse(inputStream);
    }

    /**
     * 解析 manifest.xml 文件（从 Path）
     *
     * @param manifestPath manifest.xml 路径
     * @return Document 对象
     */
    public static Document parse(Path manifestPath) throws Exception {
        try (InputStream is = Files.newInputStream(manifestPath)) {
            return parse(is);
        }
    }

    /**
     * 根据 project name 获取项目信息
     *
     * @param manifestFile manifest.xml 文件
     * @param projectName project 的 name 属性
     * @return Project 信息，如果未找到返回 Optional.empty()
     */
    public static Optional<Project> getProjectByName(File manifestFile, String projectName) {
        try {
            Document doc = parse(manifestFile);
            return getProjectByNameFromDoc(doc, projectName);
        } catch (Exception e) {
            logger.error("解析 manifest.xml 失败: {}", manifestFile, e);
            return Optional.empty();
        }
    }

    /**
     * 根据 project name 获取项目信息（从 InputStream）
     *
     * @param inputStream manifest.xml 输入流
     * @param projectName project 的 name 属性
     * @return Project 信息，如果未找到返回 Optional.empty()
     */
    public static Optional<Project> getProjectByName(InputStream inputStream, String projectName) {
        try {
            Document doc = parse(inputStream);
            return getProjectByNameFromDoc(doc, projectName);
        } catch (Exception e) {
            logger.error("解析 manifest.xml 失败", e);
            return Optional.empty();
        }
    }

    /**
     * 根据 project name 获取项目信息（从 Path）
     *
     * @param manifestPath manifest.xml 路径
     * @param projectName project 的 name 属性
     * @return Project 信息，如果未找到返回 Optional.empty()
     */
    public static Optional<Project> getProjectByName(Path manifestPath, String projectName) {
        try {
            Document doc = parse(manifestPath);
            return getProjectByNameFromDoc(doc, projectName);
        } catch (Exception e) {
            logger.error("解析 manifest.xml 失败: {}", manifestPath, e);
            return Optional.empty();
        }
    }

    /**
     * 从 Document 中根据 name 获取 project
     */
    private static Optional<Project> getProjectByNameFromDoc(Document doc, String projectName) {
        NodeList projectNodes = doc.getElementsByTagName("project");
        for (int i = 0; i < projectNodes.getLength(); i++) {
            Element project = (Element) projectNodes.item(i);
            String name = project.getAttribute("name");
            if (projectName.equals(name)) {
                return Optional.of(parseProject(project));
            }
        }
        return Optional.empty();
    }

    /**
     * 根据 project path 获取项目信息
     *
     * @param manifestFile manifest.xml 文件
     * @param projectPath  project 的 path 属性
     * @return Project 信息，如果未找到返回 Optional.empty()
     */
    public static Optional<Project> getProjectByPath(File manifestFile, String projectPath) {
        try {
            Document doc = parse(manifestFile);
            return getProjectByPathFromDoc(doc, projectPath);
        } catch (Exception e) {
            logger.error("解析 manifest.xml 失败: {}", manifestFile, e);
            return Optional.empty();
        }
    }

    /**
     * 根据 project path 获取项目信息（从 Path）
     *
     * @param manifestPath manifest.xml 路径
     * @param projectPath  project 的 path 属性
     * @return Project 信息，如果未找到返回 Optional.empty()
     */
    public static Optional<Project> getProjectByPath(Path manifestPath, String projectPath) {
        try {
            Document doc = parse(manifestPath);
            return getProjectByPathFromDoc(doc, projectPath);
        } catch (Exception e) {
            logger.error("解析 manifest.xml 失败: {}", manifestPath, e);
            return Optional.empty();
        }
    }

    /**
     * 从 Document 中根据 path 获取 project
     */
    private static Optional<Project> getProjectByPathFromDoc(Document doc, String projectPath) {
        NodeList projectNodes = doc.getElementsByTagName("project");
        for (int i = 0; i < projectNodes.getLength(); i++) {
            Element project = (Element) projectNodes.item(i);
            String path = project.getAttribute("path");
            if (projectPath.equals(path)) {
                return Optional.of(parseProject(project));
            }
        }
        return Optional.empty();
    }

    /**
     * 解析 project 节点
     */
    private static Project parseProject(Element project) {
        String name = project.getAttribute("name");
        String path = project.getAttribute("path");
        String revision = project.getAttribute("revision");
        String upstream = project.getAttribute("upstream");
        String remote = project.getAttribute("remote");
        String groups = project.getAttribute("groups");

        return new Project(
                name.isEmpty() ? null : name,
                path.isEmpty() ? null : path,
                revision.isEmpty() ? null : revision,
                upstream.isEmpty() ? null : upstream,
                remote.isEmpty() ? null : remote,
                groups.isEmpty() ? null : groups
        );
    }

    /**
     * 获取所有 project 列表
     *
     * @param manifestFile manifest.xml 文件
     * @return 所有 project 列表
     */
    public static List<Project> getAllProjects(File manifestFile) {
        List<Project> projects = new ArrayList<>();
        try {
            Document doc = parse(manifestFile);
            NodeList projectNodes = doc.getElementsByTagName("project");
            for (int i = 0; i < projectNodes.getLength(); i++) {
                Element project = (Element) projectNodes.item(i);
                projects.add(parseProject(project));
            }
        } catch (Exception e) {
            logger.error("解析 manifest.xml 失败: {}", manifestFile, e);
        }
        return projects;
    }

    /**
     * 获取所有 project 列表（从 Path）
     *
     * @param manifestPath manifest.xml 路径
     * @return 所有 project 列表
     */
    public static List<Project> getAllProjects(Path manifestPath) {
        List<Project> projects = new ArrayList<>();
        try {
            Document doc = parse(manifestPath);
            NodeList projectNodes = doc.getElementsByTagName("project");
            for (int i = 0; i < projectNodes.getLength(); i++) {
                Element project = (Element) projectNodes.item(i);
                projects.add(parseProject(project));
            }
        } catch (Exception e) {
            logger.error("解析 manifest.xml 失败: {}", manifestPath, e);
        }
        return projects;
    }

    /**
     * 根据 project name 获取 revision
     *
     * @param manifestFile manifest.xml 文件
     * @param projectName  project 的 name 属性
     * @return revision 值，如果未找到返回 Optional.empty()
     */
    public static Optional<String> getRevisionByName(File manifestFile, String projectName) {
        return getProjectByName(manifestFile, projectName)
                .map(Project::getRevision)
                .filter(r -> r != null && !r.isEmpty());
    }

    /**
     * 根据 project name 获取 upstream
     *
     * @param manifestFile manifest.xml 文件
     * @param projectName  project 的 name 属性
     * @return upstream 值，如果未找到返回 Optional.empty()
     */
    public static Optional<String> getUpstreamByName(File manifestFile, String projectName) {
        return getProjectByName(manifestFile, projectName)
                .map(Project::getUpstream)
                .filter(u -> u != null && !u.isEmpty());
    }

    /**
     * 根据 project path 获取 revision
     *
     * @param manifestFile manifest.xml 文件
     * @param projectPath  project 的 path 属性
     * @return revision 值，如果未找到返回 Optional.empty()
     */
    public static Optional<String> getRevisionByPath(File manifestFile, String projectPath) {
        return getProjectByPath(manifestFile, projectPath)
                .map(Project::getRevision)
                .filter(r -> r != null && !r.isEmpty());
    }

    /**
     * 根据 project path 获取 upstream
     *
     * @param manifestFile manifest.xml 文件
     * @param projectPath  project 的 path 属性
     * @return upstream 值，如果未找到返回 Optional.empty()
     */
    public static Optional<String> getUpstreamByPath(File manifestFile, String projectPath) {
        return getProjectByPath(manifestFile, projectPath)
                .map(Project::getUpstream)
                .filter(u -> u != null && !u.isEmpty());
    }
}
