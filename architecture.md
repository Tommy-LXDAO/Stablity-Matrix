# Stability Matrix - 架构设计文档

本项目是一个Android崩溃分析平台，用于解析tombstone文件并使用AI进行故障分析。

## 项目概述

Stability Matrix是一个用于分析Android应用崩溃日志的系统。它能够：
- 解析Android tombstone文件（ANR/崩溃日志）
- 支持AArch64架构的寄存器解析
- 使用AI进行故障分析
- 提供RESTful API接口

## 技术栈

| 层级 | 技术 |
|------|------|
| 框架 | Spring Boot 3.5.8 |
| AI支持 | Spring AI 1.1.1 |
| Web框架 | Spring WebFlux / Spring MVC |
| 缓存 | Spring Data Redis |
| 构建工具 | Maven |
| 测试 | JUnit 1.12.2, Mockito |
| JSON处理 | FastJSON 2.0.43 |
| 文件处理 | Apache Commons CSV 1.10.0, Apache Commons Compress 1.26.0 |

## 系统架构

### 整体分层架构

```
┌─────────────────────────────────────────────────────────────┐
│                      Controller Layer                       │
│  AnalysisController | SessionController | TestController   │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                       Service Layer                          │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐          │
│  │  FileService│  │AIFileAnalysis│ │SessionService│          │
│  │  (Interface)│  │  (Interface)│  │ (Interface) │          │
│  └─────────────┘  └─────────────┘  └─────────────┘          │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐          │
│  │PatternMatch │  │ArchiveExtract│ │ResourceReader│          │
│  │ (Interface) │  │ (Interface) │  │ (Interface) │          │
│  └─────────────┘  └─────────────┘  └─────────────┘          │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                    Implementation Layer                     │
│  AndroidAArch64FileServiceImpl  │  SpringResourceReaderServiceImpl │
│  SIGSEGVPatternMatcher         │  RedisSessionServiceImpl         │
│  CommonsCompressArchiveService │  OpenAIFileAnalysisServiceImpl    │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                      Data/Config Layer                       │
│  Redis | File System | ClassPathResource | External AI API │
└─────────────────────────────────────────────────────────────┘
```

### 现有Service接口/类

| 服务名 | 类型 | 说明 |
|--------|------|------|
| FileService | Interface | 文件解析服务接口 |
| AndroidAArch64FileService | Class (implements FileService) | Android AArch64文件解析实现 |
| ResourceReaderService | Interface | 资源读取服务接口 |
| SpringResourceReaderServiceImpl | Class (implements ResourceReaderService) | Spring资源读取实现 |
| PatternMatchService | Interface | 模式匹配服务接口 |
| SignalPatternMatcher | Interface | 信号模式匹配器接口 |
| SessionService | Class | 会话管理服务 |
| AIFileAnalysisService | Class | AI文件分析服务 |
| AITroubleAnalysisService | Class | AI故障分析服务 |
| SessionFileStorageService | Class | 会话文件存储服务 |
| ArchiveExtractionService | Class | 归档提取服务 |

## API端点

### 1. SessionController - 会话管理

| 端点 | 方法 | 说明 |
|------|------|------|
| `/session/create` | POST | 创建新会话 |

### 2. AnalysisController - AI分析

| 端点 | 方法 | 说明 |
|------|------|------|
| `/ai/analyze` | POST | AI分析接口 |

### 3. TestController - 测试

| 端点 | 方法 | 说明 |
|------|------|------|
| `/test/*` | GET/POST | 测试接口 |

## 数据模型

### 核心实体

1. **TroubleEntity** - 通用故障实体
2. **AArch64Tombstone** - AArch64架构tombstone文件解析结果
   - SignalInfo - 信号信息
   - StackDumpInfo - 堆栈回溯信息
   - RegisterDumpInfo - 寄存器转储信息
   - SpecialRegisterInfo - 特殊寄存器信息
   - FdInfo - 文件描述符信息
3. **AArch64RegisterDumpInfo** - AArch64寄存器信息

### DTO

- AIAnalysisResponse - AI分析响应
- SessionResponse - 会话响应

## 配置管理

### application.yaml 关键配置

```yaml
server:
  port: 8888

spring:
  ai:
    openai-sdk:
      api-key: ${NEWAPI_KEY:${OPENAI_API_KEY:}}
      base-url: ${NEWAPI_BASE_URL:${OPENAI_BASE_URL:https://api.openai.com}}
  data:
    redis:
      host: 127.0.0.1
      port: 6379

file:
  storage:
    base-path: /tmp/sessions
```

## 工作流程

### 文件分析流程

```
用户上传文件
    │
    ▼
SessionController 创建会话
    │
    ▼
AnalysisController 接收分析请求
    │
    ▼
AIFileAnalysisService 解析文件
    │
    ├── AndroidAArch64FileService 解析tombstone
    │
    ├── PatternMatchService 匹配信号类型
    │
    └── AI分析故障原因
    │
    ▼
返回分析结果
```

## 模式匹配器

项目包含针对不同信号类型的模式匹配器：

- SIGILLPatternMatcher - 非法指令信号
- SIGPIPEPatternMatcher - 管道信号
- SIGABRTPatternMatcher - 中止信号
- SIGSEGVPatternMatcher - 段错误信号
- SIGBUSPatternMatcher - 总线错误信号
- SIGFPEPatternMatcher - 浮点异常信号

## 未来规划

### 待重构为接口的服务

1. SessionService → ISessionService + 实现类
2. AIFileAnalysisService → IAIFileAnalysisService + 实现类
3. AITroubleAnalysisService → IAITroubleAnalysisService + 实现类
4. SessionFileStorageService → ISessionFileStorageService + 实现类
5. ArchiveExtractionService → IArchiveExtractionService + 实现类

### 平台适配

- 设计PlatformDetector接口
- 支持Android和Linux平台
- 动态选择文件解析实现

## 测试策略

### 测试分层

1. **单元测试** - 针对每个Service接口的实现
2. **集成测试** - 使用@SpringBootTest
3. **Mock测试** - 使用Mockito模拟依赖

### 测试目录结构

```
src/test/java/com/stability/martrix/
├── ApplicationTest.java
└── service/
    ├── AndroidAArch64FileServiceTest.java
    ├── AndroidAArch64FileServiceSpringBootTest.java
    └── AITroubleAnalysisServiceTest.java
```
