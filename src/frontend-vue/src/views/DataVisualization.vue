<template>
  <div class="visualization-page">
    <!-- 顶部工具栏 -->
    <div class="header">
      <div class="toolbar-left">
        <a-button type="primary" @click="showEntityModal = true">
          <PlusOutlined /> 添加实体
        </a-button>
        <a-button type="primary" @click="showRelationModal = true" style="margin-left: 8px;">
          <LinkOutlined /> 添加关系
        </a-button>
        <a-divider type="vertical" />
        <a-select
          v-model:value="selectedOntology"
          placeholder="请选择本体"
          style="width: 160px; margin-right: 8px;"
          @change="handleOntologyChange"
        >
          <a-select-option v-for="item in ontologyList" :key="item.id" :value="item.id">
            {{ item.name }}
          </a-select-option>
        </a-select>
        <a-select v-model:value="nodeLimit" style="width: 130px;">
          <a-select-option value="100">节点数量100</a-select-option>
          <a-select-option value="200">节点数量200</a-select-option>
          <a-select-option value="300">节点数量300</a-select-option>
          <a-select-option value="400">节点数量400</a-select-option>
          <a-select-option value="500">节点数量500</a-select-option>
        </a-select>
      </div>
      <div class="toolbar-right">
        <a-input-search
          v-model:value="searchQuery"
          placeholder="输入关键词搜索"
          style="width: 280px;"
          @search="handleSearch"
          allow-clear
        />
      </div>
    </div>

    <!-- 主体内容 -->
    <div class="main-container">
      <!-- 左侧功能面板 -->
      <div class="left-panel">
        <div class="panel-title">
          <FilterOutlined /> 智能分析
        </div>

        <!-- 搜索结果 -->
        <div v-if="searchResults.keyword" class="section">
          <div class="section-title">搜索结果</div>
          <div class="search-info">
            <div class="info-item">
              <span class="label">关键词:</span>
              <span class="value">"{{ searchResults.keyword }}"</span>
            </div>
            <div class="info-item">
              <span class="label">搜索结果:</span>
              <span class="value success">{{ searchResults.nodes.length }} 个节点和 {{ searchResults.edges.length }} 条边</span>
            </div>
            <div class="info-item">
              <span class="label">已高亮:</span>
              <span class="value info">{{ searchResults.highlightedNodes }} 个节点和 {{ searchResults.highlightedEdges }} 条边</span>
            </div>
          </div>
        </div>

        <!-- 数据导入导出和入库 -->
        <div class="section">
          <div class="section-title">数据导入导出和入库</div>
          <a-select v-model:value="exportFormat" placeholder="选择导出格式" style="width: 100%; margin-bottom: 8px;">
            <a-select-option value="json">JSON格式</a-select-option>
            <a-select-option value="rdf">RDF/XML格式</a-select-option>
            <a-select-option value="owl">OWL/XML格式</a-select-option>
            <a-select-option value="turtle">Turtle格式</a-select-option>
          </a-select>
          <div class="btn-group">
            <a-upload :show-upload-list="false" :customRequest="handleImport">
              <a-button type="primary" block><UploadOutlined /> 导入</a-button>
            </a-upload>
            <a-button type="primary" block @click="handleExport"><DownloadOutlined /> 导出</a-button>
            <a-button type="primary" block @click="handleWarehouse"><DatabaseOutlined /> 入库</a-button>
          </div>
        </div>

        <!-- 属性过滤 -->
        <div class="section">
          <div class="section-title">属性过滤</div>
          <div class="form-item logic-operator">
            <label>逻辑操作符:</label>
            <a-radio-group v-model:value="filterLogic" size="small">
              <a-radio-button value="AND">且</a-radio-button>
              <a-radio-button value="OR">或</a-radio-button>
            </a-radio-group>
          </div>
          <div class="form-item">
            <label>过滤条件:</label>
            <div class="conditions-box">
              <div v-for="(condition, index) in filterConditions" :key="index" class="condition-item">
                <a-select v-model:value="condition.propertyName" placeholder="属性名称" size="small" style="width: 100%; margin-bottom: 4px;">
                  <a-select-option value="name">名称</a-select-option>
                  <a-select-option value="type">类型</a-select-option>
                  <a-select-option value="iri">IRI</a-select-option>
                </a-select>
                <a-select v-model:value="condition.operator" placeholder="操作符" size="small" style="width: 100%; margin-bottom: 4px;">
                  <a-select-option value="equals">等于</a-select-option>
                  <a-select-option value="contains">包含</a-select-option>
                </a-select>
                <div style="display: flex; gap: 4px;">
                  <a-input v-model:value="condition.value" placeholder="过滤值" size="small" style="flex: 1;" />
                  <a-button danger size="small" @click="removeFilterCondition(index)"><DeleteOutlined /></a-button>
                </div>
              </div>
              <div v-if="filterConditions.length === 0" class="empty-text">暂无过滤条件</div>
            </div>
            <a-button type="dashed" size="small" block @click="addFilterCondition" style="margin-top: 8px;">
              <PlusOutlined /> 添加条件
            </a-button>
          </div>
          <div style="display: flex; gap: 8px; margin-top: 8px;">
            <a-button type="primary" style="flex: 1;" @click="applyPropertyFilter">
              执行过滤
            </a-button>
            <a-button @click="clearPropertyFilter">
              清除
            </a-button>
          </div>
        </div>

        <!-- 路径分析 -->
        <div class="section">
          <div class="section-title">路径分析</div>
          <a-select v-model:value="pathStart" placeholder="选择或输入起始节点" style="width: 100%; margin-bottom: 8px;"
            show-search :filter-option="filterNodeOption" option-filter-prop="label">
            <a-select-option v-for="node in graphData.nodes" :key="node.id" :value="node.id" :label="node.name">
              {{ node.name }}
            </a-select-option>
          </a-select>
          <a-select v-model:value="pathEnd" placeholder="选择或输入目标节点" style="width: 100%; margin-bottom: 8px;"
            show-search :filter-option="filterNodeOption" option-filter-prop="label">
            <a-select-option v-for="node in graphData.nodes" :key="node.id" :value="node.id" :label="node.name">
              {{ node.name }}
            </a-select-option>
          </a-select>
          <div class="btn-group">
            <a-button type="primary" block @click="findShortestPath">最短路径</a-button>
            <a-button type="primary" block @click="findAllPaths">全通路径</a-button>
            <a-button danger block @click="clearHighlight">清除高亮</a-button>
          </div>
        </div>

        <!-- 模式匹配 -->
        <div class="section">
          <div class="section-title">模式匹配</div>
          <a-select
            mode="multiple"
            placeholder="从图谱中选择节点"
            style="width: 100%; margin-bottom: 8px;"
            v-model:value="selectedGraphNodes"
            :options="graphData.nodes.map(n => ({ label: n.name, value: n.id }))"
          />
          <a-select
            mode="multiple"
            placeholder="从图谱中选择边"
            style="width: 100%; margin-bottom: 8px;"
            v-model:value="selectedGraphEdges"
          >
            <a-select-option v-for="(edge, index) in graphData.links" :key="index" :value="index">
              {{ edge.source }} - {{ edge.target }}
            </a-select-option>
          </a-select>
          <div class="selected-preview" v-if="selectedGraphNodes.length || selectedGraphEdges.length">
            <div v-if="selectedGraphNodes.length">
              <label>已选节点 ({{ selectedGraphNodes.length }}):</label>
              <div class="tags">
                <a-tag v-for="nodeId in selectedGraphNodes" :key="nodeId" closable @close="removeSelectedNode(nodeId)">
                  {{ getNodeLabel(nodeId) }}
                </a-tag>
              </div>
            </div>
          </div>
          <div class="btn-group">
            <a-button danger block @click="clearPatternSelection">清除选择</a-button>
            <a-button type="primary" block @click="applyPatternMatch">执行模式匹配</a-button>
          </div>
        </div>

        <!-- 图分析 -->
        <div class="section">
          <div class="section-title">图分析</div>
          
          <!-- 图谱结构分析 -->
          <div class="analysis-item">
            <label>图谱结构分析</label>
            <a-button type="primary" size="small" block @click="analyzeGraphStructure">
              <BarChartOutlined /> 分析结构
            </a-button>
          </div>

          <!-- 实体扩线 -->
          <div class="analysis-item">
            <label>实体扩线</label>
            <a-select v-model:value="expandParams.entityId" placeholder="选择或输入实体" size="small" style="width: 100%; margin-bottom: 4px;"
              show-search :filter-option="filterNodeOption" option-filter-prop="label">
              <a-select-option v-for="node in graphData.nodes" :key="node.id" :value="node.id" :label="node.name">
                {{ node.name }}
              </a-select-option>
            </a-select>
            <a-input-number v-model:value="expandParams.level" :min="1" :max="5" size="small" style="width: 100%; margin-bottom: 4px;" placeholder="扩展层级" />
            <a-select v-model:value="expandParams.direction" size="small" style="width: 100%; margin-bottom: 4px;">
              <a-select-option value="BOTH">双向</a-select-option>
              <a-select-option value="OUT"> outgoing</a-select-option>
              <a-select-option value="IN"> incoming</a-select-option>
            </a-select>
            <a-button type="primary" size="small" block @click="expandEntity" :disabled="!expandParams.entityId">
              <NodeExpandOutlined /> 扩线分析
            </a-button>
          </div>

          <!-- 邻居查询 -->
          <div class="analysis-item">
            <label>邻居查询</label>
            <a-select v-model:value="neighborParams.nodeId" placeholder="选择或输入节点" size="small" style="width: 100%; margin-bottom: 4px;"
              show-search :filter-option="filterNodeOption" option-filter-prop="label">
              <a-select-option v-for="node in graphData.nodes" :key="node.id" :value="node.id" :label="node.name">
                {{ node.name }}
              </a-select-option>
            </a-select>
            <a-input-number v-model:value="neighborParams.depth" :min="1" :max="3" size="small" style="width: 100%; margin-bottom: 4px;" placeholder="查询深度" />
            <a-button type="primary" size="small" block @click="queryNeighbors" :disabled="!neighborParams.nodeId">
              <ApartmentOutlined /> 查询邻居
            </a-button>
          </div>

          <!-- 社区发现 -->
          <div class="analysis-item">
            <label>社区发现</label>
            <a-select v-model:value="communityParams.algorithm" size="small" style="width: 100%; margin-bottom: 4px;">
              <a-select-option value="LOUVAIN">Louvain算法</a-select-option>
              <a-select-option value="LABEL_PROPAGATION">标签传播</a-select-option>
              <a-select-option value="MODULARITY_OPTIMIZATION">模块度优化</a-select-option>
            </a-select>
            <a-button type="primary" size="small" block @click="discoverCommunities">
              <TeamOutlined /> 发现社区
            </a-button>
          </div>

          <!-- 中心度分析 -->
          <div class="analysis-item">
            <label>中心度分析</label>
            <a-select v-model:value="centralityParams.type" size="small" style="width: 100%; margin-bottom: 4px;">
              <a-select-option value="DEGREE">度中心度</a-select-option>
              <a-select-option value="BETWEENNESS">介数中心度</a-select-option>
              <a-select-option value="CLOSENESS">接近中心度</a-select-option>
              <a-select-option value="EIGENVECTOR">特征向量中心度</a-select-option>
              <a-select-option value="PAGERANK">PageRank</a-select-option>
            </a-select>
            <a-button type="primary" size="small" block @click="analyzeCentrality">
              <DotChartOutlined /> 分析中心度
            </a-button>
          </div>

          <!-- 关联查询 -->
          <div class="analysis-item">
            <label>关联查询</label>
            <a-select mode="multiple" v-model:value="relationQueryParams.nodeIds" placeholder="选择或输入多个节点" size="small" style="width: 100%; margin-bottom: 4px;"
              show-search :filter-option="filterNodeOption" option-filter-prop="label">
              <a-select-option v-for="node in graphData.nodes" :key="node.id" :value="node.id" :label="node.name">
                {{ node.name }}
              </a-select-option>
            </a-select>
            <a-select v-model:value="relationQueryParams.relationType" size="small" style="width: 100%; margin-bottom: 4px;">
              <a-select-option value="ALL">所有关系</a-select-option>
              <a-select-option value="DIRECT">直接关系</a-select-option>
              <a-select-option value="INDIRECT">间接关系</a-select-option>
            </a-select>
            <a-button type="primary" size="small" block @click="queryRelations" :disabled="relationQueryParams.nodeIds.length < 2">
              <LinkOutlined /> 关联查询
            </a-button>
          </div>
        </div>
      </div>

      <!-- 中间图谱 -->
      <div class="center-panel">
        <!-- echarts 容器 - 始终存在 -->
        <div ref="chartContainer" class="chart-container"></div>
        
        <!-- 空状态覆盖层 -->
        <div v-if="!selectedOntology" class="empty-chart-overlay">
          <DatabaseOutlined style="font-size: 64px; color: #d9d9d9;" />
          <div class="empty-title">请选择本体</div>
          <div class="empty-desc">从上方下拉框选择一个本体以查看图谱</div>
        </div>
        <div v-else-if="loading" class="empty-chart-overlay">
          <a-spin size="large" />
          <div class="empty-title">正在加载图谱数据…</div>
          <div class="empty-desc">全图扫描与渲染约需十几秒，请稍候</div>
        </div>
        <div v-else-if="graphData.nodes.length === 0" class="empty-chart-overlay">
          <ReloadOutlined style="font-size: 64px; color: #d9d9d9;" />
          <div class="empty-title">暂无数据</div>
          <div class="empty-desc">
            该本体暂无图谱数据，请先执行图谱导入：
            <code>mvn test -Dgraph.import=true -Dtest=RdfBulkImportTest</code>
          </div>
        </div>
        
        <div class="chart-toolbar" v-if="selectedOntology && graphData.nodes.length > 0">
          <a-button size="small" @click="resetChart"><ReloadOutlined /> 重置视图</a-button>
        </div>
        <div class="node-status" v-if="selectedNode.id">
          <a-tag color="success">已选中: {{ selectedNode.name }}</a-tag>
        </div>
        <div class="node-status" v-else-if="selectedOntology && graphData.nodes.length > 0">
          <a-tag>点击节点查看详情</a-tag>
        </div>
      </div>

      <!-- 右侧详情面板 -->
      <div class="right-panel" v-if="selectedNode.id || selectedEdge.id || analysisResult.visible">
        <div class="panel-header">
          <span>
            {{ analysisResult.visible ? analysisResult.title : selectedType === 'node' ? '节点详情' : '边详情' }}
          </span>
          <a-button type="text" size="small" @click="closeDetail"><CloseOutlined /></a-button>
        </div>
        <div class="panel-content">
          <!-- 图分析结果 -->
          <template v-if="analysisResult.visible">
            <div v-if="analysisResult.type === 'structure'">
              <div class="stat-item">
                <span class="label">节点总数:</span>
                <span class="value">{{ analysisResult.data.nodeCount }}</span>
              </div>
              <div class="stat-item">
                <span class="label">边总数:</span>
                <span class="value">{{ analysisResult.data.edgeCount }}</span>
              </div>
              <div class="stat-item">
                <span class="label">平均度:</span>
                <span class="value">{{ analysisResult.data.avgDegree }}</span>
              </div>
              <div class="stat-item">
                <span class="label">密度:</span>
                <span class="value">{{ analysisResult.data.density }}</span>
              </div>
            </div>
            <div v-if="analysisResult.type === 'centrality'">
              <div v-for="(item, idx) in analysisResult.data.topNodes" :key="idx" class="rank-item">
                <span class="rank">{{ idx + 1 }}</span>
                <span class="name">{{ item.name }}</span>
                <span class="score">{{ item.score.toFixed(3) }}</span>
              </div>
            </div>
            <!-- 社区发现结果 -->
            <div v-if="analysisResult.type === 'communities'">
              <!-- 统计概览 -->
              <div class="community-stats-overview">
                <div class="stat-row">
                  <div class="stat-box">
                    <div class="stat-value">{{ analysisResult.data.communities?.length || 0 }}</div>
                    <div class="stat-label">社区总数</div>
                  </div>
                  <div class="stat-box">
                    <div class="stat-value">{{ analysisResult.data.statistics?.totalNodes || 0 }}</div>
                    <div class="stat-label">总节点数</div>
                  </div>
                  <div class="stat-box">
                    <div class="stat-value">{{ (analysisResult.data.statistics?.averageCommunitySize || 0).toFixed(1) }}</div>
                    <div class="stat-label">平均社区大小</div>
                  </div>
                </div>
                <div class="stat-row">
                  <div class="stat-box">
                    <div class="stat-value">{{ (analysisResult.data.modularity || 0).toFixed(3) }}</div>
                    <div class="stat-label">模块度</div>
                  </div>
                  <div class="stat-box">
                    <div class="stat-value">{{ analysisResult.data.iterations || 0 }}</div>
                    <div class="stat-label">迭代次数</div>
                  </div>
                  <div class="stat-box">
                    <div class="stat-value">{{ analysisResult.data.algorithm || 'LOUVAIN' }}</div>
                    <div class="stat-label">算法</div>
                  </div>
                </div>
              </div>
              
              <a-divider />
              
              <!-- 社区列表 -->
              <div class="section-title">社区详情</div>
              <div class="communities-list">
                <div v-for="(community, idx) in analysisResult.data.communities" :key="idx" 
                     class="community-card" 
                     :style="{ borderLeftColor: community.color }">
                  <div class="community-card-header">
                    <div class="community-title">
                      <span class="community-index" :style="{ backgroundColor: community.color }">{{ idx + 1 }}</span>
                      <span class="community-name">社区 {{ idx + 1 }}</span>
                    </div>
                    <div class="community-badges">
                      <a-tag color="blue">{{ community.nodeCount }} 节点</a-tag>
                      <a-tag v-if="community.internalEdges" color="green">{{ community.internalEdges }} 内部边</a-tag>
                    </div>
                  </div>
                  
                  <!-- 节点类型分布 -->
                  <div v-if="getCommunityTypeStats(community.nodes)" class="community-type-distribution">
                    <div v-for="(count, type) in getCommunityTypeStats(community.nodes)" :key="type" class="type-item">
                      <span class="type-label">{{ type === 'class' ? '类' : type === 'individual' ? '实例' : type }}</span>
                      <a-progress :percent="Math.round(count / community.nodeCount * 100)" :show-info="false" size="small" />
                      <span class="type-count">{{ count }}</span>
                    </div>
                  </div>
                  
                  <!-- 节点列表 -->
                  <div class="community-nodes-list">
                    <a-tag v-for="node in (community.nodes || []).slice(0, 10)" :key="node.id" size="small" :color="community.color" class="community-node-tag">
                      {{ node.label || node.name }}
                    </a-tag>
                    <a-tag v-if="community.nodeCount > 10" size="small" class="more-nodes-tag">
                      +{{ community.nodeCount - 10 }} 更多
                    </a-tag>
                  </div>
                  
                  <!-- 操作按钮 -->
                  <div class="community-actions">
                    <a-button type="link" size="small" @click="highlightCommunity(community)">
                      <EyeOutlined /> 高亮显示
                    </a-button>
                    <a-button type="link" size="small" @click="showCommunityDetails(community)">
                      <InfoCircleOutlined /> 查看详情
                    </a-button>
                  </div>
                </div>
              </div>
            </div>
            <!-- 实体扩线结果 -->
            <div v-if="analysisResult.type === 'expand'">
              <div class="stat-item">
                <span class="label">中心实体:</span>
                <span class="value" style="color: #ff4d4f; font-weight: bold;">{{ analysisResult.data.centerEntity }}</span>
              </div>
              <div class="stat-item">
                <span class="label">扩展层级:</span>
                <span class="value">{{ analysisResult.data.expandLevel }} 层</span>
              </div>
              <div class="stat-item">
                <span class="label">扩展方向:</span>
                <span class="value">
                  <a-tag :color="analysisResult.data.direction === 'OUT' ? 'green' : analysisResult.data.direction === 'IN' ? 'orange' : 'blue'">
                    {{ analysisResult.data.direction === 'OUT' ? '出边 (下游)' : analysisResult.data.direction === 'IN' ? '入边 (上游)' : '双向' }}
                  </a-tag>
                </span>
              </div>
              <div class="stat-item">
                <span class="label">总节点数:</span>
                <span class="value" style="color: #52c41a; font-weight: bold;">{{ analysisResult.data.totalNodes }}</span>
              </div>
              <div class="stat-item">
                <span class="label">总边数:</span>
                <span class="value">{{ analysisResult.data.totalEdges }}</span>
              </div>
              <a-divider />
              <div class="section-title" style="font-size: 13px; margin-bottom: 10px;">按层级查看节点</div>
              <div class="expand-levels">
                <div v-for="level in analysisResult.data.expandLevel + 1" :key="level - 1" class="expand-level-section">
                  <div class="expand-level-header">
                    <span class="expand-level-title">{{ level === 1 ? '中心节点 (第0层)' : `第 ${level - 1} 层` }}</span>
                    <a-tag size="small" color="blue">
                      {{ analysisResult.data.nodes.filter(n => n.level === level - 1).length }} 个
                    </a-tag>
                  </div>
                  <div class="expand-level-nodes">
                    <a-tag v-for="node in analysisResult.data.nodes.filter(n => n.level === level - 1).slice(0, 8)" 
                           :key="node.id" 
                           :color="node.isCenter ? 'red' : 'green'" 
                           size="small"
                           class="expand-node-tag">
                      {{ node.label }}
                    </a-tag>
                    <span v-if="analysisResult.data.nodes.filter(n => n.level === level - 1).length > 8" 
                          class="more-nodes">
                      +{{ analysisResult.data.nodes.filter(n => n.level === level - 1).length - 8 }} 个...
                    </span>
                  </div>
                </div>
              </div>
            </div>
            <!-- 邻居查询结果 -->
            <div v-if="analysisResult.type === 'neighbors'">
              <div class="stat-item">
                <span class="label">中心节点:</span>
                <span class="value">{{ analysisResult.data.centerNode }}</span>
              </div>
              <div class="stat-item">
                <span class="label">邻居数量:</span>
                <span class="value">{{ analysisResult.data.neighborCount }}</span>
              </div>
              <div class="neighbor-list">
                <a-tag v-for="node in analysisResult.data.neighbors" :key="node.id" size="small" color="blue">
                  {{ node.name }}
                </a-tag>
              </div>
            </div>
            <!-- 关联查询结果 -->
            <div v-if="analysisResult.type === 'associations'">
              <div class="stat-item">
                <span class="label">查询类型:</span>
                <span class="value">{{ analysisResult.data.queryType === 'DIRECT' ? '直接关联' : analysisResult.data.queryType === 'INDIRECT' ? '间接关联' : '全部关联' }}</span>
              </div>
              <div class="stat-item">
                <span class="label">关联数量:</span>
                <span class="value" style="color: #1890ff; font-weight: bold;">{{ analysisResult.data.totalAssociations || 0 }}</span>
              </div>
              <div class="stat-item">
                <span class="label">选中节点:</span>
                <span class="value">{{ analysisResult.data.nodeNames?.join(', ') }}</span>
              </div>
              <a-divider />
              <div v-if="analysisResult.data.associations?.length > 0" class="association-list">
                <div v-for="(assoc, idx) in analysisResult.data.associations" :key="idx" class="association-item">
                  <div class="association-header">
                    <span class="association-title">关联 {{ idx + 1 }}</span>
                    <a-tag size="small" :color="assoc.type === 'DIRECT' ? 'green' : 'orange'">{{ assoc.type }}</a-tag>
                  </div>
                  <div class="association-nodes">
                    <a-tag size="small">{{ getNodeLabel(assoc.source) }}</a-tag>
                    <span style="margin: 0 8px;">→</span>
                    <a-tag size="small">{{ getNodeLabel(assoc.target) }}</a-tag>
                  </div>
                </div>
              </div>
              <div v-else class="empty-text">未找到关联关系</div>
            </div>
            <!-- 模式匹配结果 -->
            <div v-if="analysisResult.type === 'pattern'">
              <div class="stat-item">
                <span class="label">模式节点数:</span>
                <span class="value">{{ analysisResult.data.patternNodeCount }}</span>
              </div>
              <div class="stat-item">
                <span class="label">模式边数:</span>
                <span class="value">{{ analysisResult.data.patternEdgeCount }}</span>
              </div>
              <div class="stat-item">
                <span class="label">匹配结果数:</span>
                <span class="value" style="color: #722ed1; font-weight: bold;">{{ analysisResult.data.matchCount }}</span>
              </div>
              <a-divider />
              <div class="match-list">
                <div v-for="(match, idx) in analysisResult.data.matches.slice(0, 5)" :key="idx" class="match-item">
                  <div class="match-header">
                    <span class="match-title">匹配 {{ idx + 1 }}</span>
                    <span class="match-size">{{ match.length }} 个节点</span>
                  </div>
                  <div class="match-nodes">
                    <a-tag v-for="nodeId in match.slice(0, 3)" :key="nodeId" size="small" color="purple">
                      {{ getNodeLabel(nodeId) }}
                    </a-tag>
                    <span v-if="match.length > 3">+{{ match.length - 3 }}</span>
                  </div>
                </div>
                <div v-if="analysisResult.data.matches.length > 5" class="match-more">
                  还有 {{ analysisResult.data.matches.length - 5 }} 个匹配...
                </div>
              </div>
            </div>
          </template>

          <!-- 节点详情 -->
          <template v-if="selectedType === 'node'">
            <a-form layout="vertical">
              <a-form-item label="节点ID">
                <a-input v-model:value="selectedNode.id" disabled />
              </a-form-item>
              <a-form-item label="名称">
                <a-input v-model:value="selectedNode.name" />
              </a-form-item>
              <a-form-item label="IRI">
                <a-input v-model:value="selectedNode.iri" placeholder="请输入节点的IRI" />
              </a-form-item>
              <a-form-item label="类型">
                <a-select v-model:value="selectedNode.type">
                  <a-select-option value="class">类</a-select-option>
                  <a-select-option value="individual">实例</a-select-option>
                  <a-select-option value="property">属性</a-select-option>
                </a-select>
              </a-form-item>
              <a-form-item label="描述">
                <a-textarea v-model:value="selectedNode.description" :rows="3" />
              </a-form-item>
              <a-form-item label="重要性">
                <a-slider v-model:value="selectedNode.importance" :min="1" :max="10" />
              </a-form-item>
              <a-form-item label="样式设置">
                <div class="style-settings">
                  <div class="style-item">
                    <span>颜色:</span>
                    <input type="color" v-model="selectedNode.color" class="color-picker" />
                  </div>
                  <div class="style-item">
                    <span>大小:</span>
                    <a-slider v-model:value="selectedNode.size" :min="10" :max="100" />
                  </div>
                </div>
              </a-form-item>
              <a-form-item>
                <a-button type="primary" @click="saveNode"><CheckOutlined /> 保存</a-button>
                <a-button danger @click="deleteNode" style="margin-left: 8px;"><DeleteOutlined /> 删除</a-button>
              </a-form-item>
            </a-form>
          </template>

          <!-- 边详情 -->
          <template v-else-if="selectedType === 'edge'">
            <a-alert
              :type="selectedEdge.editable ? 'success' : 'warning'"
              :message="selectedEdge.editable ? '当前关系可编辑' : '当前关系不可编辑'"
              show-icon
              style="margin-bottom: 16px;"
            />
            <a-form layout="vertical">
              <a-form-item label="边ID">
                <a-input v-model:value="selectedEdge.id" disabled />
              </a-form-item>
              <a-form-item label="关系名称">
                <a-input v-model:value="selectedEdge.label" :disabled="!selectedEdge.editable" />
              </a-form-item>
              <a-form-item label="源节点">
                <a-input v-model:value="selectedEdge.source" disabled />
              </a-form-item>
              <a-form-item label="目标节点">
                <a-input v-model:value="selectedEdge.target" disabled />
              </a-form-item>
              <a-form-item label="关系类型">
                <a-select v-model:value="selectedEdge.type" disabled>
                  <a-select-option value="subClassOf">继承</a-select-option>
                  <a-select-option value="instanceOf">实例</a-select-option>
                  <a-select-option value="objectProperty">对象属性</a-select-option>
                </a-select>
              </a-form-item>
              <a-form-item label="描述">
                <a-textarea v-model:value="selectedEdge.description" :rows="3" :disabled="!selectedEdge.editable" />
              </a-form-item>
              <a-form-item label="权重">
                <a-slider v-model:value="selectedEdge.weight" :min="0.1" :max="5" :step="0.1" />
              </a-form-item>
              <a-form-item>
                <a-button type="primary" @click="saveEdge" :disabled="!selectedEdge.editable"><CheckOutlined /> 保存</a-button>
                <a-button danger @click="deleteEdge" style="margin-left: 8px;"><DeleteOutlined /> 删除</a-button>
              </a-form-item>
            </a-form>
          </template>
        </div>
      </div>
    </div>

    <!-- 添加实体弹窗 -->
    <a-modal v-model:open="showEntityModal" title="添加实体" @ok="confirmAddEntity" width="500px">
      <a-form layout="vertical">
        <a-form-item label="实体名称" required>
          <a-input v-model:value="newEntity.name" placeholder="请输入实体名称" />
        </a-form-item>
        <a-form-item label="实体类型" required>
          <a-select v-model:value="newEntity.type">
            <a-select-option value="class">类 (Class)</a-select-option>
            <a-select-option value="individual">实例 (Individual)</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="IRI">
          <a-input v-model:value="newEntity.iri" placeholder="请输入完整的IRI" />
        </a-form-item>
        <a-form-item label="描述">
          <a-textarea v-model:value="newEntity.description" :rows="3" />
        </a-form-item>
      </a-form>
    </a-modal>

    <!-- 添加关系弹窗 -->
    <a-modal v-model:open="showRelationModal" title="添加关系" @ok="confirmAddRelation" width="500px">
      <a-form layout="vertical">
        <a-form-item label="源节点" required>
          <a-select v-model:value="newRelation.sourceNodeId" placeholder="选择或输入源节点"
            show-search :filter-option="filterNodeOption" option-filter-prop="label">
            <a-select-option v-for="node in graphData.nodes" :key="node.id" :value="node.id" :label="node.name + ' (' + node.id + ')'">
              {{ node.name }} ({{ node.id }})
            </a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="目标节点" required>
          <a-select v-model:value="newRelation.targetNodeId" placeholder="选择或输入目标节点"
            show-search :filter-option="filterNodeOption" option-filter-prop="label">
            <a-select-option v-for="node in graphData.nodes" :key="node.id" :value="node.id" :label="node.name + ' (' + node.id + ')'">
              {{ node.name }} ({{ node.id }})
            </a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="关系类型" required>
          <a-select v-model:value="newRelation.relationType">
            <a-select-option value="subClassOf">继承 (subClassOf)</a-select-option>
            <a-select-option value="instanceOf">实例 (instanceOf)</a-select-option>
            <a-select-option value="objectProperty">对象属性</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="关系名称">
          <a-input v-model:value="newRelation.relationName" placeholder="请输入关系名称" />
        </a-form-item>
      </a-form>
    </a-modal>

    <!-- 入库弹窗 -->
    <a-modal v-model:open="showWarehouseModal" title="知识图谱入库" @ok="confirmWarehouse" width="400px">
      <a-form layout="vertical">
        <a-form-item label="图名称">
          <a-input v-model:value="warehouseForm.graphName" disabled />
        </a-form-item>
        <a-form-item label="当前版本">
          <a-input v-model:value="warehouseForm.currentVersion" disabled />
        </a-form-item>
        <a-form-item label="新版本号">
          <a-input v-model:value="warehouseForm.newVersion" placeholder="如：1.1.0" />
        </a-form-item>
        <a-form-item label="版本说明">
          <a-textarea v-model:value="warehouseForm.remark" :rows="3" />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, nextTick, h } from 'vue'
import * as echarts from 'echarts'
import {
  PlusOutlined,
  LinkOutlined,
  SearchOutlined,
  FilterOutlined,
  UploadOutlined,
  DownloadOutlined,
  DatabaseOutlined,
  DeleteOutlined,
  CloseOutlined,
  CheckOutlined,
  ReloadOutlined,
  BarChartOutlined,
  NodeExpandOutlined,
  ApartmentOutlined,
  TeamOutlined,
  DotChartOutlined,
  EyeOutlined,
  InfoCircleOutlined
} from '@ant-design/icons-vue'
import { message, Modal } from 'ant-design-vue'
import { warehouseGraph, getPageOntology, getOntologyVisualization, exportOntologyFile } from '@/api/ontology'
import { findNeighbors as apiFindNeighbors } from '@/api/graph'
import { detectCommunities, analyzeCentrality as apiAnalyzeCentrality, queryNeighbors as apiQueryNeighbors, queryAssociations, expandEntity as apiExpandEntity } from '@/api/graphAnalysis'

// ========== 数据 ==========
const ontologyList = ref([])


// ========== 状态变量 ==========
const selectedOntology = ref(null)
const nodeLimit = ref('100')
const searchQuery = ref('')
const chartContainer = ref(null)
let myChart = null

// 图谱数据
// 加载状态
const loading = ref(false)

const graphData = reactive({
  nodes: [],
  links: [],
  edges: []  // 与links保持同步
})

// 选中状态
const selectedType = ref('') // 'node' | 'edge'
const selectedNode = reactive({
  id: '',
  name: '',
  iri: '',
  type: 'individual',
  description: '',
  importance: 5,
  color: '#52c41a',
  size: 25
})
const selectedEdge = reactive({
  id: '',
  source: '',
  target: '',
  label: '',
  type: '',
  description: '',
  weight: 1,
  editable: true
})

// 搜索和过滤
const searchResults = reactive({
  keyword: '',
  nodes: [],
  edges: [],
  highlightedNodes: 0,
  highlightedEdges: 0
})

// 属性过滤
const filterLogic = ref('AND')
const filterConditions = ref([
  { propertyName: 'name', operator: 'contains', value: '' }
])

// 路径分析
const pathStart = ref('')
const pathEnd = ref('')

// 模式匹配
const selectedGraphNodes = ref([])
const selectedGraphEdges = ref([])

// 导入导出
const exportFormat = ref('json')

// 弹窗控制
const showEntityModal = ref(false)
const showRelationModal = ref(false)
const showWarehouseModal = ref(false)

// 表单数据
const newEntity = reactive({
  name: '',
  type: 'class',
  iri: '',
  description: ''
})

const newRelation = reactive({
  sourceNodeId: '',
  targetNodeId: '',
  relationType: 'objectProperty',
  relationName: ''
})

const warehouseForm = reactive({
  graphName: '',
  currentVersion: '1.0.0',
  newVersion: '',
  remark: ''
})

// 图分析参数
const expandParams = reactive({
  entityId: '',
  level: 2,
  direction: 'BOTH'
})

const neighborParams = reactive({
  nodeId: '',
  depth: 1
})

const communityParams = reactive({
  algorithm: 'LOUVAIN'
})

const centralityParams = reactive({
  type: 'DEGREE'
})

const relationQueryParams = reactive({
  nodeIds: [],
  relationType: 'ALL'
})

// 分析结果
const analysisResult = reactive({
  visible: false,
  type: '',
  title: '',
  data: null
})

// ========== 方法 ==========

// 节点搜索过滤函数
const filterNodeOption = (input, option) => {
  const label = option.label || option.children?.[0]?.children || ''
  return label.toLowerCase().includes(input.toLowerCase())
}

// 初始化图表
const initChart = () => {
  if (!chartContainer.value) return
  
  myChart = echarts.init(chartContainer.value)
  
  const option = {
    tooltip: {
      trigger: 'item',
      formatter: (params) => {
        if (params.dataType === 'node') {
          return `<div><strong>${params.data.name}</strong><br/>类型: ${params.data.type}</div>`
        } else {
          return `<div><strong>${params.data.label || params.data.value || '关系'}</strong></div>`
        }
      }
    },
    series: [{
      type: 'graph',
      layout: 'force',
      data: [],
      links: [],
      roam: true,
      draggable: true,
      label: { show: true, position: 'bottom' },
      force: {
        repulsion: 1000,
        edgeLength: 150,
        gravity: 0.1
      },
      lineStyle: {
        curveness: 0.1
      },
      emphasis: {
        focus: 'adjacency'
      }
    }]
  }
  
  myChart.setOption(option)
  
  // 点击事件
  myChart.on('click', (params) => {
    if (params.dataType === 'node') {
      handleNodeClick(params.data)
    } else if (params.dataType === 'edge') {
      handleEdgeClick(params.data)
    }
  })
  
  // 空白处点击关闭详情
  myChart.getZr().on('click', (event) => {
    if (!event.target) {
      closeDetail()
    }
  })
  
  window.addEventListener('resize', () => myChart.resize())
}

// 渲染图表
const renderChart = () => {
  if (!myChart) return
  myChart.setOption({
    series: [{
      data: graphData.nodes,
      links: graphData.links
    }]
  })
}

// 本体切换
// 加载本体列表
const loadOntologyList = async () => {
  try {
    const result = await getPageOntology({ pageNum: 1, pageSize: 100 })
    if (result.data && result.data.records) {
      ontologyList.value = result.data.records.map(item => ({
        id: item.id,
        name: item.projectName
      }))

      // 进入页面直接出图：默认打开「中哈」本体，用户不必先点一下下拉框。
      //
      // 选择优先级：名称含「中哈」→ 列表只有 1 条时取该条 → 否则不自动选。
      // 最后一条是刻意的：库里若将来有多个本体，替用户猜一个更可能猜错，
      // 不如保持空状态提示「请选择本体」，避免看错数据还以为页面坏了。
      const preferred =
        ontologyList.value.find(item => (item.name || '').includes('中哈')) ||
        (ontologyList.value.length === 1 ? ontologyList.value[0] : null)
      if (preferred) {
        selectedOntology.value = preferred.id
        // silent：进页面不弹「已加载 N 个节点」，但「暂无数据」/报错照常提示
        await loadOntologyGraph(preferred.id, { silent: true })
      }
    }
  } catch (error) {
    console.error('加载本体列表失败:', error)
    message.error('加载本体列表失败')
  }
}

/**
 * 清空图谱，并让模板里的空状态覆盖层（empty-chart-overlay）显示出来。
 *
 * 注意不能只清 graphData：echarts 实例里还留着上一次渲染的图，否则会从
 * 覆盖层底下露出来，看起来像"有数据"。
 */
const clearGraph = () => {
  graphData.nodes = []
  graphData.links = []
  graphData.edges = []
  if (myChart) {
    myChart.clear()
  }
}

/**
 * 拉取指定本体的图谱数据并渲染。
 *
 * 从 handleOntologyChange 里抽出来，让「进入页面自动打开默认本体」与
 * 「用户手动切换本体」共用同一段逻辑。两者唯一的差别是 silent：
 * 进页面时静默加载，不弹「已加载 N 个节点」的提示（每次进图谱页都弹一次
 * 属于噪声）；用户主动切换时照常提示。「暂无数据」与报错的提示则一律保留，
 * 因为它们是用户必须知道的状态。
 */
const loadOntologyGraph = async (ontologyId, { silent = false } = {}) => {
  if (!ontologyId) return

  try {
    loading.value = true
    const result = await getOntologyVisualization(ontologyId)
    
    console.log('API返回结果:', result)
    console.log('result.data:', result.data)
    console.log('result.data.nodes:', result.data?.nodes)
    console.log('result.data.edges:', result.data?.edges)
    
    // 适配后端返回的数据结构
    const responseData = result.data || result
    const nodeList = responseData.nodes || []
    const edgeList = responseData.edges || []
    
    if (nodeList.length > 0) {
      // 转换后端数据为echarts格式
      const nodes = nodeList.map(node => ({
        id: node.id,
        name: node.label || node.id,
        value: node.label || node.id,
        type: node.type || 'individual',
        symbolSize: node.size || (node.type === 'class' ? 40 : 25),
        itemStyle: { color: node.color || (node.type === 'class' ? '#1890ff' : '#52c41a') },
        iri: node.uri || '',
        draggable: true,
        // 保留原始数据
        data: node.data || {},
        level: node.level,
        parentId: node.parentId
      }))
      
      const links = edgeList.map(edge => ({
        id: edge.id,
        source: edge.source,
        target: edge.target,
        label: edge.label || edge.name || '',  // 保持字符串格式
        type: edge.type || '',
        lineStyle: { color: edge.color || '#999999' },
        value: edge.label || edge.name || ''
      }))
      
      graphData.nodes = nodes
      graphData.links = links
      graphData.edges = links // 兼容两种命名
      renderChart()
      if (!silent) {
        message.success(`已加载 ${nodes.length} 个节点, ${links.length} 条关系`)
      }
    } else {
      // 【已移除假数据兜底】原先这里会调 generateMockData() 画一张编造的图谱
      // （产品/供应商/订单、iPhone 15、富士康、订单2024001、张三…），
      // 只弹一个轻描淡写的 warning。
      // 结项演示时这会造成「看起来有数据、实则全部虚构」，与后端
      // InitDataController 写 Math.random() 属同一类问题，故彻底去掉：
      // 清空后由模板里的空状态覆盖层显示"暂无数据"。
      clearGraph()
      message.warning('该本体暂无图谱数据，请先执行图谱导入（见 docs/服务器部署指南.md）')
    }
  } catch (error) {
    console.error('加载图谱数据失败:', error)
    message.error('加载图谱数据失败: ' + (error.message || '未知错误'))
    // 同样不再用假数据兜底，如实显示空状态
    clearGraph()
  } finally {
    loading.value = false
  }
}

/**
 * 用户在下拉框里手动切换本体。
 *
 * 注意签名：模板写的是 `@change="handleOntologyChange"`，ant-design-vue 会把
 * 选中值作为第一个实参传进来。因此这里**不能**声明 `(silent = false)` 之类的
 * 形参 —— 那会被选中值（数字）覆盖成真值，导致切换本体时静默不提示。
 * 静默开关只在内部调用 loadOntologyGraph 时显式传入。
 */
const handleOntologyChange = async () => {
  if (!selectedOntology.value) {
    message.warning('请先选择本体')
    return
  }
  await loadOntologyGraph(selectedOntology.value)
}

// 节点点击
const handleNodeClick = (nodeData) => {
  selectedType.value = 'node'
  Object.assign(selectedNode, {
    id: nodeData.id,
    name: nodeData.name,
    iri: nodeData.iri || '',
    type: nodeData.type || 'individual',
    description: nodeData.description || '',
    importance: nodeData.importance || 5,
    color: nodeData.itemStyle?.color || '#52c41a',
    size: nodeData.symbolSize || 25
  })
  
  // 高亮
  myChart.dispatchAction({
    type: 'highlight',
    seriesIndex: 0,
    dataIndex: graphData.nodes.findIndex(n => n.id === nodeData.id)
  })
  
  // 打开面板后重新调整图表大小
  setTimeout(() => {
    myChart && myChart.resize()
  }, 310)
}

// 边点击
const handleEdgeClick = (edgeData) => {
  selectedType.value = 'edge'
  Object.assign(selectedEdge, {
    id: edgeData.id,
    source: edgeData.source,
    target: edgeData.target,
    label: edgeData.label || edgeData.value || '',
    type: edgeData.type || 'objectProperty',
    description: edgeData.description || '',
    weight: edgeData.weight || 1,
    editable: edgeData.editable !== false
  })
  
  // 打开面板后重新调整图表大小
  setTimeout(() => {
    myChart && myChart.resize()
  }, 310)
}

// 关闭详情
const closeDetail = () => {
  selectedType.value = ''
  selectedNode.id = ''
  selectedEdge.id = ''
  analysisResult.visible = false
  myChart.dispatchAction({ type: 'downplay', seriesIndex: 0 })
  // 关闭面板后重新调整图表大小
  setTimeout(() => {
    myChart && myChart.resize()
  }, 310)
}

// 搜索
const handleSearch = () => {
  if (!searchQuery.value) {
    clearSearch()
    return
  }
  
  const keyword = searchQuery.value.toLowerCase()
  const matchedNodes = graphData.nodes.filter(n => 
    n.name.toLowerCase().includes(keyword)
  )
  
  searchResults.keyword = searchQuery.value
  searchResults.nodes = matchedNodes
  searchResults.highlightedNodes = matchedNodes.length
  
  // 高亮匹配节点
  const indices = matchedNodes.map(n => graphData.nodes.findIndex(node => node.id === n.id))
  myChart.dispatchAction({ type: 'downplay', seriesIndex: 0 })
  indices.forEach(index => {
    myChart.dispatchAction({ type: 'highlight', seriesIndex: 0, dataIndex: index })
  })
  
  message.success(`搜索到 ${matchedNodes.length} 个节点`)
}

const clearSearch = () => {
  searchQuery.value = ''
  searchResults.keyword = ''
  searchResults.nodes = []
  searchResults.highlightedNodes = 0
  myChart.dispatchAction({ type: 'downplay', seriesIndex: 0 })
}

// 属性过滤
const addFilterCondition = () => {
  filterConditions.value.push({ propertyName: 'name', operator: 'contains', value: '' })
}

const removeFilterCondition = (index) => {
  filterConditions.value.splice(index, 1)
}

// 属性过滤 - 前端实现
const applyPropertyFilter = () => {
  if (filterConditions.value.length === 0) {
    // 没有条件，重置所有节点显示
    resetNodeHighlight()
    message.info('已清除过滤条件')
    return
  }
  
  // 执行过滤
  const matchedNodes = []
  const unmatchedNodes = []
  
  graphData.nodes.forEach(node => {
    const isMatch = evaluateFilterConditions(node, filterConditions.value, filterLogic.value)
    if (isMatch) {
      matchedNodes.push(node.id)
    } else {
      unmatchedNodes.push(node.id)
    }
  })
  
  // 应用高亮效果
  highlightFilteredNodes(matchedNodes, unmatchedNodes)
  
  message.success(`过滤完成，找到 ${matchedNodes.length} 个匹配节点`)
}

// 评估单个节点是否满足过滤条件
const evaluateFilterConditions = (node, conditions, logic) => {
  const results = conditions.map(condition => {
    const nodeValue = getNodePropertyValue(node, condition.propertyName)
    const filterValue = condition.value.toLowerCase()
    
    if (!filterValue) return true // 空值不过滤
    
    const nodeValueStr = String(nodeValue || '').toLowerCase()
    
    switch (condition.operator) {
      case 'equals':
        return nodeValueStr === filterValue
      case 'contains':
        return nodeValueStr.includes(filterValue)
      default:
        return true
    }
  })
  
  return logic === 'AND' 
    ? results.every(r => r) 
    : results.some(r => r)
}

// 获取节点属性值
const getNodePropertyValue = (node, propertyName) => {
  switch (propertyName) {
    case 'name':
      return node.name || node.value || ''
    case 'type':
      return node.type || ''
    case 'iri':
      return node.iri || ''
    default:
      return node[propertyName] || ''
  }
}

// 高亮过滤后的节点
const highlightFilteredNodes = (matchedIds, unmatchedIds) => {
  if (!myChart) return
  
  // 更新节点样式
  const updatedNodes = graphData.nodes.map(node => {
    const isMatched = matchedIds.includes(node.id)
    return {
      ...node,
      itemStyle: {
        ...node.itemStyle,
        opacity: isMatched ? 1 : 0.2,  // 不匹配节点变透明
        borderColor: isMatched ? '#ff4d4f' : undefined,
        borderWidth: isMatched ? 2 : 0
      },
      label: {
        show: isMatched,
        fontSize: isMatched ? 14 : 12,
        fontWeight: isMatched ? 'bold' : 'normal'
      }
    }
  })
  
  // 更新边样式
  const updatedLinks = graphData.links.map(link => {
    const sourceMatched = matchedIds.includes(link.source)
    const targetMatched = matchedIds.includes(link.target)
    const isVisible = sourceMatched && targetMatched
    
    return {
      ...link,
      lineStyle: {
        ...link.lineStyle,
        opacity: isVisible ? 1 : 0.1,
        width: isVisible ? 2 : 1
      },
      label: {
        ...link.label,
        show: isVisible
      }
    }
  })
  
  myChart.setOption({
    series: [{
      data: updatedNodes,
      links: updatedLinks
    }]
  })
}

// 清除属性过滤
const clearPropertyFilter = () => {
  filterConditions.value = []
  resetNodeHighlight()
  message.info('已清除过滤条件')
}

// 重置节点高亮
const resetNodeHighlight = () => {
  if (!myChart) return
  
  const resetNodes = graphData.nodes.map(node => ({
    ...node,
    itemStyle: {
      color: node.itemStyle?.color || (node.type === 'class' ? '#1890ff' : '#52c41a'),
      opacity: 1,
      borderWidth: 0
    },
    label: {
      show: true,
      fontSize: 12,
      fontWeight: 'normal'
    }
  }))
  
  const resetLinks = graphData.links.map(link => ({
    ...link,
    lineStyle: {
      ...link.lineStyle,
      opacity: 1,
      width: 1
    }
  }))
  
  myChart.setOption({
    series: [{
      data: resetNodes,
      links: resetLinks
    }]
  })
}

// 路径分析 - 前端实现（BFS算法）
const findShortestPath = async () => {
  if (!pathStart.value || !pathEnd.value) {
    message.warning('请选择起始和目标节点')
    return
  }
  if (pathStart.value === pathEnd.value) {
    message.warning('起始节点和目标节点不能相同')
    return
  }
  
  try {
    message.loading('正在查找最短路径...', 0)
    
    // 在前端使用BFS算法查找最短路径
    const path = bfsShortestPath(pathStart.value, pathEnd.value)
    
    message.destroy()
    
    if (path && path.length > 0) {
      highlightPath(path, 'shortest')
      message.success(`找到最短路径，经过 ${path.length} 个节点`)
    } else {
      message.warning('未找到路径')
    }
  } catch (error) {
    message.destroy()
    console.error('查找最短路径失败:', error)
    message.error('查找失败: ' + (error.message || '未知错误'))
  }
}

// 查找所有路径 - 前端实现（DFS算法）
const findAllPaths = async () => {
  if (!pathStart.value || !pathEnd.value) {
    message.warning('请选择起始和目标节点')
    return
  }
  if (pathStart.value === pathEnd.value) {
    message.warning('起始节点和目标节点不能相同')
    return
  }
  
  try {
    message.loading('正在查找所有路径...', 0)
    
    // 在前端使用DFS算法查找所有路径
    const maxDepth = 5  // 最大深度限制
    const maxPaths = 10 // 最大路径数量限制
    const paths = dfsAllPathsWithLimit(pathStart.value, pathEnd.value, maxDepth, maxPaths)
    
    message.destroy()
    
    if (paths && paths.length > 0) {
      // 高亮所有路径
      paths.forEach((path, index) => {
        setTimeout(() => highlightPath(path, 'all', index), index * 200)
      })
      
      message.success(`找到 ${paths.length} 条路径`)
    } else {
      message.warning('未找到路径')
    }
  } catch (error) {
    message.destroy()
    console.error('查找所有路径失败:', error)
    message.error('查找失败: ' + (error.message || '未知错误'))
  }
}

// BFS最短路径算法
const bfsShortestPath = (startId, endId) => {
  const queue = [[startId]]
  const visited = new Set([startId])
  
  while (queue.length > 0) {
    const path = queue.shift()
    const currentId = path[path.length - 1]
    
    if (currentId === endId) {
      return path
    }
    
    // 找到当前节点的邻居
    const neighbors = getNeighbors(currentId)
    
    for (const neighbor of neighbors) {
      if (!visited.has(neighbor)) {
        visited.add(neighbor)
        queue.push([...path, neighbor])
      }
    }
  }
  
  return null // 未找到路径
}

// DFS查找所有路径（带深度限制）
const dfsAllPaths = (startId, endId, maxDepth = 5) => {
  const allPaths = []
  
  const dfs = (currentId, path, depth) => {
    if (depth > maxDepth) return
    if (currentId === endId) {
      allPaths.push([...path])
      return
    }
    
    const neighbors = getNeighbors(currentId)
    for (const neighbor of neighbors) {
      if (!path.includes(neighbor)) { // 避免环
        path.push(neighbor)
        dfs(neighbor, path, depth + 1)
        path.pop()
      }
    }
  }
  
  dfs(startId, [startId], 0)
  return allPaths
}

// DFS查找所有路径（带深度限制和路径数量限制）
const dfsAllPathsWithLimit = (startId, endId, maxDepth = 5, maxPaths = 10) => {
  const allPaths = []
  
  const dfs = (currentId, path, depth) => {
    // 如果已达到最大路径数，停止搜索
    if (allPaths.length >= maxPaths) return
    // 如果超过最大深度，停止搜索
    if (depth > maxDepth) return
    // 如果到达目标节点，保存路径
    if (currentId === endId) {
      allPaths.push([...path])
      return
    }
    
    const neighbors = getNeighbors(currentId)
    for (const neighbor of neighbors) {
      if (!path.includes(neighbor)) { // 避免环
        path.push(neighbor)
        dfs(neighbor, path, depth + 1)
        path.pop()
      }
    }
  }
  
  dfs(startId, [startId], 0)
  return allPaths
}

// 获取节点的邻居
const getNeighbors = (nodeId) => {
  const neighbors = new Set()
  
  graphData.links.forEach(link => {
    if (link.source === nodeId) {
      neighbors.add(link.target)
    } else if (link.target === nodeId) {
      neighbors.add(link.source)
    }
  })
  
  return Array.from(neighbors)
}

// 高亮路径
const highlightPath = (path, type, index = 0) => {
  if (!myChart || path.length < 2) return
  
  const pathNodeIds = new Set(path)
  const pathEdgeIds = new Set()
  
  // 找到路径上的边
  for (let i = 0; i < path.length - 1; i++) {
    const edge = graphData.links.find(l => 
      (l.source === path[i] && l.target === path[i + 1]) ||
      (l.source === path[i + 1] && l.target === path[i])
    )
    if (edge) pathEdgeIds.add(edge.id)
  }
  
  // 颜色配置
  const colors = ['#ff4d4f', '#52c41a', '#1890ff', '#faad14', '#722ed1']
  const pathColor = type === 'shortest' ? '#ff4d4f' : colors[index % colors.length]
  
  // 更新节点样式
  const updatedNodes = graphData.nodes.map(node => {
    const inPath = pathNodeIds.has(node.id)
    return {
      ...node,
      itemStyle: {
        ...node.itemStyle,
        opacity: inPath ? 1 : 0.2,
        borderColor: inPath ? pathColor : undefined,
        borderWidth: inPath ? 3 : 0,
        shadowBlur: inPath ? 10 : 0,
        shadowColor: inPath ? pathColor : undefined
      },
      symbolSize: inPath ? (node.symbolSize || 25) + 10 : (node.symbolSize || 25),
      label: {
        show: inPath,
        fontSize: inPath ? 14 : 12,
        fontWeight: inPath ? 'bold' : 'normal',
        color: inPath ? pathColor : undefined
      }
    }
  })
  
  // 更新边样式
  const updatedLinks = graphData.links.map(link => {
    const inPath = pathEdgeIds.has(link.id)
    return {
      ...link,
      lineStyle: {
        ...link.lineStyle,
        color: inPath ? pathColor : '#999',
        width: inPath ? 4 : 1,
        opacity: inPath ? 1 : 0.1,
        curveness: 0
      },
      label: {
        ...link.label,
        show: inPath,
        fontSize: 12,
        color: pathColor
      }
    }
  })
  
  myChart.setOption({
    series: [{
      data: updatedNodes,
      links: updatedLinks
    }]
  })
}

const clearHighlight = () => {
  if (!myChart) return
  
  // 重置所有节点和边的样式
  const resetNodes = graphData.nodes.map(node => ({
    ...node,
    itemStyle: {
      color: node.itemStyle?.color || (node.type === 'class' ? '#1890ff' : '#52c41a'),
      opacity: 1,
      borderWidth: 0,
      shadowBlur: 0
    },
    symbolSize: node.symbolSize || (node.type === 'class' ? 40 : 25),
    label: {
      show: true,
      fontSize: 12,
      fontWeight: 'normal',
      color: undefined
    }
  }))
  
  const resetLinks = graphData.links.map(link => ({
    ...link,
    lineStyle: {
      ...link.lineStyle,
      color: '#999999',
      width: 1,
      opacity: 1,
      curveness: 0.1
    },
    label: {
      ...link.label,
      show: true
    }
  }))
  
  myChart.setOption({
    series: [{
      data: resetNodes,
      links: resetLinks
    }]
  })
  
  message.success('已清除高亮')
}

// 模式匹配
const getNodeLabel = (nodeId) => {
  const node = graphData.nodes.find(n => n.id === nodeId)
  return node ? node.name : nodeId
}

const removeSelectedNode = (nodeId) => {
  const index = selectedGraphNodes.value.indexOf(nodeId)
  if (index > -1) {
    selectedGraphNodes.value.splice(index, 1)
  }
}

const clearPatternSelection = () => {
  selectedGraphNodes.value = []
  selectedGraphEdges.value = []
}

// 执行模式匹配
const applyPatternMatch = () => {
  if (selectedGraphNodes.value.length === 0) {
    message.warning('请至少选择一个节点')
    return
  }
  
  // 构建模式子图
  const patternNodes = selectedGraphNodes.value
  const patternEdges = selectedGraphEdges.value.map(index => {
    const edge = graphData.links[index]
    return {
      source: edge.source,
      target: edge.target
    }
  })
  
  // 如果选择了边，验证边的节点是否在已选节点中
  const validPatternEdges = patternEdges.filter(edge => 
    patternNodes.includes(edge.source) && patternNodes.includes(edge.target)
  )
  
  // 在完整图谱中查找所有匹配的子图
  const matches = findPatternMatches(patternNodes, validPatternEdges)
  
  if (matches.length > 0) {
    // 高亮所有匹配结果
    highlightPatternMatches(matches)
    message.success(`模式匹配完成，找到 ${matches.length} 个匹配`)
  } else {
    message.warning('未找到匹配的子图')
  }
}

// 查找模式匹配（子图同构检测）
const findPatternMatches = (patternNodes, patternEdges) => {
  const matches = []
  
  // 获取模式的邻接关系
  const patternAdj = buildAdjacencyMap(patternNodes, patternEdges)
  
  // 获取完整图谱的邻接关系
  const graphAdj = buildAdjacencyMap(
    graphData.nodes.map(n => n.id),
    graphData.links.map(l => ({ source: l.source, target: l.target }))
  )
  
  // 如果模式只有一个节点，直接返回所有同名或同类型的节点
  if (patternNodes.length === 1) {
    const patternNodeId = patternNodes[0]
    const patternNode = graphData.nodes.find(n => n.id === patternNodeId)
    if (!patternNode) return []
    
    // 查找所有相同类型或相同名称的节点
    return graphData.nodes
      .filter(n => n.id !== patternNodeId && (n.type === patternNode.type || n.name === patternNode.name))
      .map(n => [n.id])
  }
  
  // 对于更复杂的模式，使用回溯算法查找匹配
  // 简化策略：查找包含所有模式节点且连接关系相同的子图
  const candidateNodes = findCandidateNodes(patternNodes, patternAdj, graphAdj)
  
  if (candidateNodes.length >= patternNodes.length) {
    // 尝试找到完整的结构匹配
    const nodeCombinations = generateCombinations(candidateNodes, patternNodes.length)
    
    for (const combination of nodeCombinations) {
      if (isStructureMatch(patternNodes, patternAdj, combination, graphAdj)) {
        matches.push(combination)
      }
    }
  }
  
  return matches
}

// 构建邻接关系图
const buildAdjacencyMap = (nodes, edges) => {
  const adj = {}
  nodes.forEach(nodeId => {
    adj[nodeId] = new Set()
  })
  
  edges.forEach(edge => {
    if (adj[edge.source]) adj[edge.source].add(edge.target)
    if (adj[edge.target]) adj[edge.target].add(edge.source) // 无向图
  })
  
  return adj
}

// 查找候选节点（根据类型和度数匹配）
const findCandidateNodes = (patternNodes, patternAdj, graphAdj) => {
  const candidates = []
  
  patternNodes.forEach(patternNodeId => {
    const patternNode = graphData.nodes.find(n => n.id === patternNodeId)
    if (!patternNode) return
    
    const patternDegree = patternAdj[patternNodeId]?.size || 0
    
    // 在完整图谱中查找候选节点：相同类型且度数相近
    const nodeCandidates = graphData.nodes
      .filter(n => {
        if (n.id === patternNodeId) return false // 排除自身
        const graphDegree = graphAdj[n.id]?.size || 0
        return n.type === patternNode.type && Math.abs(graphDegree - patternDegree) <= 1
      })
      .map(n => n.id)
    
    candidates.push(...nodeCandidates)
  })
  
  // 去重并返回
  return [...new Set(candidates)]
}

// 生成节点组合
const generateCombinations = (arr, k) => {
  const result = []
  
  const combine = (start, current) => {
    if (current.length === k) {
      result.push([...current])
      return
    }
    
    for (let i = start; i < arr.length; i++) {
      current.push(arr[i])
      combine(i + 1, current)
      current.pop()
    }
  }
  
  combine(0, [])
  return result
}

// 检查结构是否匹配
const isStructureMatch = (patternNodes, patternAdj, candidateNodes, graphAdj) => {
  // 建立模式节点到候选节点的映射
  const mapping = {}
  patternNodes.forEach((patternNodeId, index) => {
    mapping[patternNodeId] = candidateNodes[index]
  })
  
  // 检查每条边的关系是否保持
  for (const patternNodeId of patternNodes) {
    const patternNeighbors = patternAdj[patternNodeId] || new Set()
    const mappedNodeId = mapping[patternNodeId]
    const graphNeighbors = graphAdj[mappedNodeId] || new Set()
    
    // 检查模式中的邻居是否在图中也有对应
    for (const patternNeighborId of patternNeighbors) {
      const mappedNeighborId = mapping[patternNeighborId]
      if (!mappedNeighborId) return false
      if (!graphNeighbors.has(mappedNeighborId)) return false
    }
  }
  
  return true
}

// 高亮模式匹配结果
const highlightPatternMatches = (matches) => {
  if (!myChart || matches.length === 0) return
  
  // 收集所有匹配节点
  const matchedNodeIds = new Set()
  matches.forEach(match => {
    match.forEach(nodeId => matchedNodeIds.add(nodeId))
  })
  
  // 颜色配置
  const matchColor = '#722ed1' // 紫色
  
  // 更新节点样式
  const updatedNodes = graphData.nodes.map(node => {
    const isMatched = matchedNodeIds.has(node.id)
    const isPatternNode = selectedGraphNodes.value.includes(node.id)
    
    return {
      ...node,
      itemStyle: {
        ...node.itemStyle,
        opacity: isMatched || isPatternNode ? 1 : 0.2,
        borderColor: isPatternNode ? '#ff4d4f' : (isMatched ? matchColor : undefined),
        borderWidth: isMatched || isPatternNode ? 3 : 0,
        shadowBlur: isMatched || isPatternNode ? 10 : 0,
        shadowColor: isPatternNode ? '#ff4d4f' : (isMatched ? matchColor : undefined)
      },
      symbolSize: isMatched || isPatternNode ? (node.symbolSize || 25) + 10 : (node.symbolSize || 25),
      label: {
        show: isMatched || isPatternNode,
        fontSize: isMatched || isPatternNode ? 14 : 12,
        fontWeight: 'bold'
      }
    }
  })
  
  // 更新边样式
  const patternEdgeIndices = new Set(selectedGraphEdges.value)
  const updatedLinks = graphData.links.map((link, index) => {
    const isPatternEdge = patternEdgeIndices.has(index)
    const isMatchedEdge = matches.some(match => {
      // 检查这条边是否连接匹配节点
      return match.includes(link.source) && match.includes(link.target)
    })
    
    return {
      ...link,
      lineStyle: {
        ...link.lineStyle,
        color: isPatternEdge ? '#ff4d4f' : (isMatchedEdge ? matchColor : '#999'),
        width: isPatternEdge ? 4 : (isMatchedEdge ? 3 : 1),
        opacity: isPatternEdge || isMatchedEdge ? 1 : 0.1
      },
      label: {
        ...link.label,
        show: isPatternEdge || isMatchedEdge
      }
    }
  })
  
  myChart.setOption({
    series: [{
      data: updatedNodes,
      links: updatedLinks
    }]
  })
  
  // 显示分析结果
  analysisResult.type = 'pattern'
  analysisResult.title = '模式匹配结果'
  analysisResult.data = {
    patternNodeCount: selectedGraphNodes.value.length,
    patternEdgeCount: selectedGraphEdges.value.length,
    matchCount: matches.length,
    matches: matches
  }
  analysisResult.visible = true
}

// 导入导出
const handleImport = ({ file }) => {
  message.success(`导入文件: ${file.name}`)
}

const handleExport = async () => {
  if (!selectedOntology.value) {
    message.warning('请先选择本体')
    return
  }
  
  try {
    message.loading('正在导出...', 0)
    
    // 格式映射：前端格式 -> 后端格式
    const formatMap = {
      'rdf': 'RDF',
      'json': 'JSON',
      'owl': 'OWL',
      'turtle': 'Turtle'
    }
    const backendFormat = formatMap[exportFormat.value] || 'JSON'
    
    // 调用导出API
    const response = await exportOntologyFile(selectedOntology.value, backendFormat)
    
    // 创建下载链接
    const mimeTypeMap = {
      'json': 'application/json',
      'rdf': 'application/rdf+xml',
      'owl': 'application/rdf+xml',
      'turtle': 'text/turtle'
    }
    const blob = new Blob([response], { 
      type: mimeTypeMap[exportFormat.value] || 'application/xml'
    })
    const url = window.URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    
    // 获取文件名
    const selectedOntologyItem = ontologyList.value.find(item => item.id === selectedOntology.value)
    const fileName = selectedOntologyItem ? selectedOntologyItem.name : 'ontology'
    const extensionMap = {
      'json': 'json',
      'rdf': 'rdf',
      'owl': 'owl',
      'turtle': 'ttl'
    }
    const extension = extensionMap[exportFormat.value] || 'rdf'
    link.download = `${fileName}_${exportFormat.value}.${extension}`
    
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    window.URL.revokeObjectURL(url)
    
    message.destroy()
    message.success('导出成功')
  } catch (error) {
    message.destroy()
    console.error('导出失败:', error)
    message.error('导出失败: ' + (error.message || '未知错误'))
  }
}

// 入库
const handleWarehouse = () => {
  // 获取选中的本体名称
  const selectedOntologyItem = ontologyList.value.find(item => item.id === selectedOntology.value)
  warehouseForm.graphName = selectedOntologyItem ? selectedOntologyItem.name : '未命名图谱'
  warehouseForm.newVersion = ''
  warehouseForm.remark = ''
  showWarehouseModal.value = true
}

const confirmWarehouse = async () => {
  if (!warehouseForm.newVersion) {
    message.warning('请输入新版本号')
    return
  }
  if (!selectedOntology.value) {
    message.warning('请先选择本体')
    return
  }
  
  // 检查是否有图谱数据 - 使用links
  console.log('graphData.nodes:', graphData.nodes)
  console.log('graphData.links:', graphData.links)
  
  if (!graphData.nodes || graphData.nodes.length === 0) {
    message.warning('当前没有图谱数据，请先加载或创建图谱')
    return
  }
  
  try {
    // 准备入库数据 - 使用links
    const warehouseData = {
      ontologyId: selectedOntology.value,
      ontologyName: warehouseForm.graphName,
      version: warehouseForm.newVersion,
      remark: warehouseForm.remark,
      nodes: graphData.nodes.map(node => ({
        id: node.id,
        name: node.name || node.value,
        type: node.type || 'individual',
        iri: node.iri || '',
        description: node.description || '',
        color: node.itemStyle?.color || '#52c41a',
        size: node.symbolSize || 25
        // 注意：不传递 data 字段，避免序列化问题
      })),
      relationships: graphData.links.map(link => ({
        id: link.id,
        source: link.source,
        target: link.target,
        relationshipName: link.label || link.value || 'relatedTo',
        relationshipType: link.type || 'objectProperty',
        description: link.description || '',
        color: link.lineStyle?.color || '#999999',
        width: link.lineStyle?.width || 1
      }))
    }
    
    // 调试：查看发送的数据
    console.log('发送的入库数据:', JSON.stringify(warehouseData, null, 2))
    
    // 调用后端API
    const response = await warehouseGraph(selectedOntology.value, warehouseData)
    
    // 后端返回 Result 结构: {code, message, data}
    const result = response.data || response
    
    if (result.status === 'success') {
      message.success(`入库成功！保存了 ${result.nodeCount} 个节点和 ${result.relationshipCount} 条关系`)
      showWarehouseModal.value = false
    } else {
      message.error(result.message || '入库失败')
    }
  } catch (error) {
    console.error('入库失败:', error)
    message.error('入库失败: ' + (error.message || '未知错误'))
  }
}

// 实体操作
const confirmAddEntity = () => {
  if (!newEntity.name) {
    message.warning('请输入实体名称')
    return
  }
  const id = `${newEntity.type}${Date.now()}`
  graphData.nodes.push({
    id,
    name: newEntity.name,
    value: newEntity.name,
    type: newEntity.type,
    symbolSize: newEntity.type === 'class' ? 40 : 25,
    itemStyle: { color: newEntity.type === 'class' ? '#1890ff' : '#52c41a' },
    iri: newEntity.iri,
    description: newEntity.description,
    draggable: true
  })
  renderChart()
  message.success('添加实体成功')
  showEntityModal.value = false
  // 重置表单
  newEntity.name = ''
  newEntity.type = 'class'
  newEntity.iri = ''
  newEntity.description = ''
}

// 关系操作
const confirmAddRelation = () => {
  if (!newRelation.sourceNodeId || !newRelation.targetNodeId) {
    message.warning('请选择源节点和目标节点')
    return
  }
  if (newRelation.sourceNodeId === newRelation.targetNodeId) {
    message.warning('源节点和目标节点不能相同')
    return
  }
  const id = `edge${Date.now()}`
  const newEdge = {
    id,
    source: newRelation.sourceNodeId,
    target: newRelation.targetNodeId,
    value: newRelation.relationName || newRelation.relationType,
    label: newRelation.relationName || newRelation.relationType,
    type: newRelation.relationType,
    editable: true
  }
  graphData.links.push(newEdge)
  graphData.edges.push(newEdge)
  renderChart()
  message.success('添加关系成功')
  showRelationModal.value = false
}

// 保存和删除
const saveNode = () => {
  const index = graphData.nodes.findIndex(n => n.id === selectedNode.id)
  if (index > -1) {
    graphData.nodes[index] = {
      ...graphData.nodes[index],
      name: selectedNode.name,
      iri: selectedNode.iri,
      type: selectedNode.type,
      description: selectedNode.description,
      importance: selectedNode.importance,
      itemStyle: { color: selectedNode.color },
      symbolSize: selectedNode.size
    }
    renderChart()
    message.success('保存成功')
  }
}

const deleteNode = () => {
  Modal.confirm({
    title: '确认删除',
    content: '确定删除该节点吗？',
    onOk: () => {
      const index = graphData.nodes.findIndex(n => n.id === selectedNode.id)
      if (index > -1) {
        graphData.nodes.splice(index, 1)
        // 删除相关边
        graphData.links = graphData.links.filter(l => 
          l.source !== selectedNode.id && l.target !== selectedNode.id
        )
        graphData.edges = graphData.edges.filter(l => 
          l.source !== selectedNode.id && l.target !== selectedNode.id
        )
        renderChart()
        closeDetail()
        message.success('删除成功')
      }
    }
  })
}

const saveEdge = () => {
  const linkIndex = graphData.links.findIndex(l => l.id === selectedEdge.id)
  const edgeIndex = graphData.edges.findIndex(l => l.id === selectedEdge.id)
  const updatedEdge = {
    ...(linkIndex > -1 ? graphData.links[linkIndex] : {}),
    label: selectedEdge.label,
    description: selectedEdge.description,
    weight: selectedEdge.weight
  }
  if (linkIndex > -1) {
    graphData.links[linkIndex] = updatedEdge
  }
  if (edgeIndex > -1) {
    graphData.edges[edgeIndex] = updatedEdge
  }
  renderChart()
  message.success('保存成功')
}

const deleteEdge = () => {
  Modal.confirm({
    title: '确认删除',
    content: '确定删除该关系吗？',
    onOk: () => {
      const linkIndex = graphData.links.findIndex(l => l.id === selectedEdge.id)
      const edgeIndex = graphData.edges.findIndex(l => l.id === selectedEdge.id)
      if (linkIndex > -1) {
        graphData.links.splice(linkIndex, 1)
      }
      if (edgeIndex > -1) {
        graphData.edges.splice(edgeIndex, 1)
      }
      renderChart()
      closeDetail()
      message.success('删除成功')
    }
  })
}

// 图分析功能 - 基于已加载的真实 graphData 本地计算（原先这里标注"使用模拟数据"，但实现一直读的是 graphData，注释有误）
const analyzeGraphStructure = () => {
  const nodeCount = graphData.nodes.length
  const edgeCount = graphData.links.length
  const avgDegree = (edgeCount * 2 / nodeCount).toFixed(2)
  const density = (edgeCount * 2 / (nodeCount * (nodeCount - 1))).toFixed(3)
  
  analysisResult.type = 'structure'
  analysisResult.title = '图谱结构分析'
  analysisResult.data = {
    nodeCount,
    edgeCount,
    avgDegree,
    density
  }
  analysisResult.visible = true
  message.success('图谱结构分析完成')
}

// 实体扩线 - 调用后端API
const expandEntity = async () => {
  if (!expandParams.entityId) {
    message.warning('请选择实体')
    return
  }
  if (!selectedOntology.value) {
    message.warning('请先选择本体')
    return
  }
  
  try {
    message.loading('正在进行实体扩线...', 0)
    
    const params = {
      entityId: expandParams.entityId,
      expandLevel: expandParams.level || 2,
      direction: expandParams.direction || 'BOTH'
    }
    
    const response = await apiExpandEntity(selectedOntology.value, params)
    message.destroy()
    
    if (response.data) {
      const result = response.data
      
      // 显示扩线结果
      analysisResult.type = 'expand'
      analysisResult.title = '实体扩线结果'
      analysisResult.data = {
        centerEntity: result.centerEntity,
        expandLevel: result.expandLevel,
        totalNodes: result.totalNodes,
        totalEdges: result.totalEdges,
        levelStatistics: result.levelStatistics,
        nodes: result.nodes || [],
        edges: result.edges || []
      }
      analysisResult.visible = true
      
      // 高亮扩线结果
      highlightExpandResult(result.nodes || [])
      
      message.success(`扩线完成，共找到 ${result.totalNodes} 个节点，${result.totalEdges} 条边`)
    } else {
      message.warning('扩线返回数据为空')
    }
  } catch (error) {
    message.destroy()
    console.error('实体扩线失败:', error)
    message.error('扩线失败: ' + (error.message || '未知错误'))
  }
}

// 高亮扩线结果
const highlightExpandResult = (expandNodes) => {
  if (!myChart || !expandNodes || expandNodes.length === 0) return
  
  const expandNodeIds = new Set(expandNodes.map(n => n.id))
  const centerId = expandParams.entityId
  
  // 更新节点样式
  const updatedNodes = graphData.nodes.map(node => {
    const isInExpand = expandNodeIds.has(node.id)
    const isCenter = node.id === centerId
    
    return {
      ...node,
      itemStyle: {
        ...node.itemStyle,
        opacity: isInExpand ? 1 : 0.1,
        borderWidth: isCenter ? 4 : (isInExpand ? 2 : 0),
        borderColor: isCenter ? '#ff4d4f' : (isInExpand ? '#52c41a' : undefined),
        shadowBlur: isCenter ? 15 : (isInExpand ? 5 : 0),
        shadowColor: isCenter ? '#ff4d4f' : (isInExpand ? '#52c41a' : undefined)
      },
      symbolSize: isCenter ? (node.symbolSize || 25) + 20 : 
                  (isInExpand ? (node.symbolSize || 25) + 10 : (node.symbolSize || 25)),
      label: {
        show: isInExpand,
        fontSize: isCenter ? 16 : (isInExpand ? 14 : 12),
        fontWeight: isCenter ? 'bold' : 'normal'
      }
    }
  })
  
  // 更新边样式
  const updatedLinks = graphData.links.map(link => {
    const sourceInExpand = expandNodeIds.has(link.source)
    const targetInExpand = expandNodeIds.has(link.target)
    const isInExpandPath = sourceInExpand && targetInExpand
    
    return {
      ...link,
      lineStyle: {
        ...link.lineStyle,
        width: isInExpandPath ? 3 : 1,
        opacity: isInExpandPath ? 1 : 0.05,
        color: isInExpandPath ? '#52c41a' : '#999'
      }
    }
  })
  
  myChart.setOption({
    series: [{
      data: updatedNodes,
      links: updatedLinks
    }]
  })
}

// 邻居查询 - 调用后端API
const queryNeighbors = async () => {
  if (!neighborParams.nodeId) {
    message.warning('请选择节点')
    return
  }
  if (!selectedOntology.value) {
    message.warning('请先选择本体')
    return
  }
  
  try {
    message.loading('正在查询邻居...', 0)
    
    // 获取选中节点的完整信息，使用 node.id 作为 nodeUri
    const selectedNodeObj = graphData.nodes.find(n => n.id === neighborParams.nodeId)
    const nodeUri = selectedNodeObj?.id || neighborParams.nodeId
    
    const params = {
      nodeUri: nodeUri,
      depth: neighborParams.depth || 1
    }
    
    const response = await apiQueryNeighbors(selectedOntology.value, params)
    message.destroy()
    
    if (response.data) {
      const result = response.data
      const neighborIds = result.neighbors || []
      
      // 获取中心节点信息
      const centerNode = graphData.nodes.find(n => n.id === neighborParams.nodeId)
      
      // 映射邻居ID到节点对象
      const neighbors = neighborIds.map(id => {
        return graphData.nodes.find(n => n.id === id) || { id, name: id }
      }).filter(Boolean)
      
      analysisResult.type = 'neighbors'
      analysisResult.title = '邻居查询结果'
      analysisResult.data = {
        centerNode: centerNode?.name || neighborParams.nodeId,
        neighborCount: neighbors.length,
        depth: result.depth,
        neighbors: neighbors
      }
      analysisResult.visible = true
      
      // 高亮邻居节点
      const neighborIdSet = new Set(neighborIds)
      const updatedNodes = graphData.nodes.map(node => ({
        ...node,
        itemStyle: {
          ...node.itemStyle,
          opacity: node.id === neighborParams.nodeId || neighborIdSet.has(node.id) ? 1 : 0.2,
          borderColor: node.id === neighborParams.nodeId ? '#ff4d4f' : (neighborIdSet.has(node.id) ? '#52c41a' : undefined),
          borderWidth: node.id === neighborParams.nodeId || neighborIdSet.has(node.id) ? 3 : 0
        }
      }))
      
      myChart.setOption({
        series: [{
          data: updatedNodes
        }]
      })
      
      message.success(`找到 ${neighbors.length} 个邻居节点`)
    } else {
      message.warning('未找到邻居节点')
    }
  } catch (error) {
    message.destroy()
    console.error('查询邻居失败:', error)
    message.error('查询失败: ' + (error.message || '未知错误'))
  }
}

// ==================== 社区发现辅助函数 ====================

// 获取社区节点类型统计
const getCommunityTypeStats = (nodes) => {
  if (!nodes || nodes.length === 0) return null
  const stats = {}
  nodes.forEach(node => {
    const type = node.type || 'unknown'
    stats[type] = (stats[type] || 0) + 1
  })
  return stats
}

// 高亮显示社区
const highlightCommunity = (community) => {
  if (!myChart || !community.nodes) return
  
  const communityNodeIds = new Set(community.nodes.map(n => n.id))
  const communityColor = community.color || '#1890ff'
  
  // 更新节点样式
  const updatedNodes = graphData.nodes.map(node => {
    const inCommunity = communityNodeIds.has(node.id)
    return {
      ...node,
      itemStyle: {
        ...node.itemStyle,
        color: inCommunity ? communityColor : '#d9d9d9',
        opacity: inCommunity ? 1 : 0.2,
        borderWidth: inCommunity ? 3 : 0,
        borderColor: inCommunity ? '#fff' : undefined
      },
      label: {
        show: inCommunity,
        fontSize: inCommunity ? 14 : 12,
        fontWeight: inCommunity ? 'bold' : 'normal'
      }
    }
  })
  
  // 更新边样式
  const updatedLinks = graphData.links.map(link => {
    const sourceIn = communityNodeIds.has(link.source)
    const targetIn = communityNodeIds.has(link.target)
    const isInternalEdge = sourceIn && targetIn
    
    return {
      ...link,
      lineStyle: {
        ...link.lineStyle,
        opacity: isInternalEdge ? 1 : 0.1,
        width: isInternalEdge ? 2 : 1,
        color: isInternalEdge ? communityColor : undefined
      }
    }
  })
  
  myChart.setOption({
    series: [{
      data: updatedNodes,
      links: updatedLinks
    }]
  })
  
  message.success(`已高亮显示社区 ${community.communityId || ''} 的 ${community.nodes.length} 个节点`)
}

// 显示社区详情
const showCommunityDetails = (community) => {
  const typeStats = getCommunityTypeStats(community.nodes)
  const typeStatsText = typeStats ? 
    Object.entries(typeStats).map(([type, count]) => {
      const typeName = type === 'class' ? '类' : type === 'individual' ? '实例' : type
      return `${typeName}: ${count}`
    }).join('，') : '暂无类型信息'
  
  Modal.info({
    title: `社区 ${community.communityId || ''} 详情`,
    width: 600,
    content: () => h('div', { style: { padding: '16px 0' } }, [
      h('div', { style: { marginBottom: '16px' } }, [
        h('div', { style: { color: '#666', marginBottom: '8px' } }, '节点数量'),
        h('div', { style: { fontSize: '24px', fontWeight: 'bold', color: '#1890ff' } }, community.nodeCount)
      ]),
      h('div', { style: { marginBottom: '16px' } }, [
        h('div', { style: { color: '#666', marginBottom: '8px' } }, '节点类型分布'),
        h('div', { style: { color: '#333' } }, typeStatsText)
      ]),
      h('div', { style: { marginBottom: '8px' } }, [
        h('div', { style: { color: '#666', marginBottom: '8px' } }, '节点列表')
      ]),
      h('div', { 
        style: { 
          maxHeight: '300px', 
          overflowY: 'auto',
          padding: '12px',
          background: '#f5f5f5',
          borderRadius: '8px'
        } 
      }, [
        h('div', {
          style: {
            display: 'flex',
            flexWrap: 'wrap',
            gap: '8px'
          }
        }, community.nodes?.map(node => 
          h('span', {
            style: {
              padding: '4px 12px',
              background: '#fff',
              border: '1px solid #d9d9d9',
              borderRadius: '4px',
              fontSize: '13px'
            }
          }, node.label || node.name || node.id)
        ) || [])
      ])
    ])
  })
}

// 社区发现 - 调用后端API
const discoverCommunities = async () => {
  if (!selectedOntology.value) {
    message.warning('请先选择本体')
    return
  }
  
  try {
    message.loading('正在进行社区发现...', 0)
    
    const params = {
      algorithm: communityParams.algorithm,
      resolution: 1.0,
      maxIterations: 100,
      minCommunitySize: 2
    }
    
    const response = await detectCommunities(selectedOntology.value, params)
    message.destroy()
    
    if (response.data) {
      const result = response.data
      
      // 转换社区数据为前端格式
      const colors = ['#1890ff', '#52c41a', '#fa8c16', '#722ed1', '#ff4d4f', '#13c2c2', '#eb2f96', '#f5222d']
      const communities = result.communityDetails?.map((detail, index) => ({
        communityId: detail.communityId || `社区 ${index + 1}`,
        nodeCount: detail.nodeCount,
        nodes: detail.nodes || [],
        sampleNodes: detail.sampleNodes || [],
        internalEdges: detail.internalEdges || 0,
        color: colors[index % colors.length]
      })) || []
      
      analysisResult.type = 'communities'
      analysisResult.title = '社区发现结果'
      analysisResult.data = { 
        communities,
        modularity: result.modularity,
        iterations: result.iterations,
        algorithm: result.algorithm,
        statistics: result.statistics
      }
      analysisResult.visible = true
      
      // 高亮社区
      highlightCommunities(result.communityDetails)
      
      message.success(`社区发现完成，找到 ${communities.length} 个社区，模块度: ${result.modularity?.toFixed(3) || 0}`)
    } else {
      message.warning('社区发现返回数据为空')
    }
  } catch (error) {
    message.destroy()
    console.error('社区发现失败:', error)
    message.error('社区发现失败: ' + (error.message || '未知错误'))
  }
}

// 高亮社区
const highlightCommunities = (communityDetails) => {
  if (!myChart || !communityDetails) return
  
  const colors = ['#1890ff', '#52c41a', '#fa8c16', '#722ed1', '#ff4d4f', '#13c2c2', '#eb2f96', '#f5222d']
  
  // 构建节点到社区的映射
  const nodeCommunityMap = new Map()
  communityDetails.forEach((detail, communityIndex) => {
    detail.nodes?.forEach(node => {
      nodeCommunityMap.set(node.id, communityIndex)
    })
  })
  
  // 更新节点样式
  const updatedNodes = graphData.nodes.map(node => {
    const communityIndex = nodeCommunityMap.get(node.id)
    const hasCommunity = communityIndex !== undefined
    
    return {
      ...node,
      itemStyle: {
        ...node.itemStyle,
        color: hasCommunity ? colors[communityIndex % colors.length] : '#999999',
        opacity: hasCommunity ? 1 : 0.3,
        borderWidth: hasCommunity ? 2 : 0,
        borderColor: hasCommunity ? '#fff' : undefined
      },
      label: {
        show: hasCommunity,
        fontSize: hasCommunity ? 14 : 12
      }
    }
  })
  
  myChart.setOption({
    series: [{
      data: updatedNodes
    }]
  })
}

// 中心度分析 - 调用后端API
const analyzeCentrality = async () => {
  if (!selectedOntology.value) {
    message.warning('请先选择本体')
    return
  }
  
  try {
    message.loading('正在进行中心度分析...', 0)
    
    const params = {
      centralityType: centralityParams.type,
      limit: 10,
      minThreshold: 0.0
    }
    
    const response = await apiAnalyzeCentrality(selectedOntology.value, params)
    message.destroy()
    
    if (response.data) {
      const result = response.data
      
      // 转换结果
      const topNodes = result.results?.map((item, index) => ({
        id: item.nodeId,
        name: item.label || item.nodeId,
        type: item.type,
        score: item.centrality,
        rank: index + 1
      })) || []
      
      analysisResult.type = 'centrality'
      const typeMap = {
        'DEGREE': '度',
        'BETWEENNESS': '介数',
        'CLOSENESS': '接近',
        'EIGENVECTOR': '特征向量',
        'PAGERANK': 'PageRank'
      }
      analysisResult.title = `${typeMap[centralityParams.type] || centralityParams.type}中心度分析`
      analysisResult.data = { 
        topNodes,
        statistics: result.statistics,
        algorithm: result.algorithm,
        totalNodes: result.totalNodes,
        qualifiedNodes: result.qualifiedNodes
      }
      analysisResult.visible = true
      
      // 高亮前3个节点
      const top3Ids = topNodes.slice(0, 3).map(n => n.id)
      highlightTopNodes(top3Ids)
      
      message.success(`中心度分析完成，分析了 ${result.totalNodes} 个节点`)
    } else {
      message.warning('中心度分析返回数据为空')
    }
  } catch (error) {
    message.destroy()
    console.error('中心度分析失败:', error)
    message.error('中心度分析失败: ' + (error.message || '未知错误'))
  }
}

// 高亮top节点
const highlightTopNodes = (topNodeIds) => {
  if (!myChart) return
  
  const topIdSet = new Set(topNodeIds)
  
  const updatedNodes = graphData.nodes.map(node => {
    const isTop = topIdSet.has(node.id)
    return {
      ...node,
      itemStyle: {
        ...node.itemStyle,
        opacity: isTop ? 1 : 0.3,
        borderWidth: isTop ? 3 : 0,
        borderColor: isTop ? '#ff4d4f' : undefined,
        shadowBlur: isTop ? 10 : 0,
        shadowColor: isTop ? '#ff4d4f' : undefined
      },
      symbolSize: isTop ? (node.symbolSize || 25) + 15 : (node.symbolSize || 25),
      label: {
        show: isTop,
        fontSize: isTop ? 16 : 12,
        fontWeight: isTop ? 'bold' : 'normal'
      }
    }
  })
  
  myChart.setOption({
    series: [{
      data: updatedNodes
    }]
  })
}

// 关联查询 - 调用后端API
const queryRelations = async () => {
  if (relationQueryParams.nodeIds.length < 2) {
    message.warning('请至少选择两个节点')
    return
  }
  
  if (!selectedOntology.value) {
    message.warning('请先选择本体')
    return
  }
  
  try {
    message.loading('正在查询关联关系...', 0)
    
    const params = {
      entityIds: relationQueryParams.nodeIds,
      queryType: relationQueryParams.relationType === 'ALL' ? 'ALL' : 
                 relationQueryParams.relationType === 'DIRECT' ? 'DIRECT' : 'INDIRECT'
    }
    
    const response = await queryAssociations(selectedOntology.value, params)
    message.destroy()
    
    if (response.data) {
      const result = response.data
      const associations = result.associations || []
      
      // 获取节点名称
      const nodeNames = relationQueryParams.nodeIds.map(id => {
        const node = graphData.nodes.find(n => n.id === id)
        return node ? node.name : id
      })
      
      analysisResult.type = 'associations'
      analysisResult.title = '关联查询结果'
      analysisResult.data = {
        nodeNames,
        associations,
        queryType: result.queryType,
        totalAssociations: associations.length
      }
      analysisResult.visible = true
      
      // 高亮关联节点和边
      highlightAssociations(relationQueryParams.nodeIds, associations)
      
      message.success(`查询到 ${associations.length} 个关联关系`)
    } else {
      message.warning('关联查询返回数据为空')
    }
  } catch (error) {
    message.destroy()
    console.error('关联查询失败:', error)
    message.error('关联查询失败: ' + (error.message || '未知错误'))
  }
}

// 高亮关联节点和边
const highlightAssociations = (nodeIds, associations) => {
  if (!myChart) return
  
  const nodeIdSet = new Set(nodeIds)
  const associationEdgeSet = new Set()
  
  associations.forEach(assoc => {
    const edgeKey = `${assoc.source}-${assoc.target}`
    associationEdgeSet.add(edgeKey)
  })
  
  // 更新节点样式
  const updatedNodes = graphData.nodes.map(node => {
    const isSelected = nodeIdSet.has(node.id)
    return {
      ...node,
      itemStyle: {
        ...node.itemStyle,
        opacity: isSelected ? 1 : 0.2,
        borderWidth: isSelected ? 3 : 0,
        borderColor: isSelected ? '#1890ff' : undefined
      },
      label: {
        show: isSelected,
        fontSize: isSelected ? 14 : 12
      }
    }
  })
  
  // 更新边样式
  const updatedLinks = graphData.links.map(link => {
    const edgeKey1 = `${link.source}-${link.target}`
    const edgeKey2 = `${link.target}-${link.source}`
    const isAssociation = associationEdgeSet.has(edgeKey1) || associationEdgeSet.has(edgeKey2)
    const isConnected = nodeIdSet.has(link.source) && nodeIdSet.has(link.target)
    
    return {
      ...link,
      lineStyle: {
        ...link.lineStyle,
        width: isAssociation ? 4 : (isConnected ? 2 : 1),
        opacity: isAssociation || isConnected ? 1 : 0.1,
        color: isAssociation ? '#ff4d4f' : (isConnected ? '#1890ff' : '#999')
      }
    }
  })
  
  myChart.setOption({
    series: [{
      data: updatedNodes,
      links: updatedLinks
    }]
  })
}

// 重置视图
const resetChart = () => {
  // 恢复初始位置和缩放，而不是清空数据
  myChart.setOption({
    series: [{
      center: null,
      zoom: 1,
      data: graphData.nodes,
      links: graphData.links
    }]
  })
  message.success('视图已重置')
}
onMounted(() => {
  nextTick(() => {
    initChart()
    // 加载本体列表
    loadOntologyList()
  })
})
</script>

<style scoped>
.visualization-page {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  background: #f0f2f5;
  padding: 16px;
  box-sizing: border-box;
  overflow: hidden;
}

/* 顶部工具栏 */
.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: #fff;
  padding: 12px 20px;
  border-radius: 8px;
  margin-bottom: 16px;
  box-shadow: 0 2px 8px rgba(0,0,0,0.06);
  border-bottom: 3px solid #096dd9;
}

.toolbar-left, .toolbar-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

/* 主体内容 */
.main-container {
  flex: 1;
  display: flex;
  gap: 12px;
  overflow: hidden;
  min-width: 0;
}

/* 左侧面板 */
.left-panel {
  width: 260px;
  min-width: 260px;
  background: #fff;
  border-radius: 8px;
  padding: 12px;
  overflow-y: auto;
  box-shadow: 0 2px 8px rgba(0,0,0,0.06);
}

.panel-title {
  font-size: 16px;
  font-weight: 600;
  color: #262626;
  margin-bottom: 16px;
  padding-bottom: 12px;
  border-bottom: 2px solid #096dd9;
  display: flex;
  align-items: center;
  gap: 8px;
}

.section {
  margin-bottom: 20px;
  padding: 12px;
  background: #fafafa;
  border-radius: 6px;
  border: 1px solid #e8e8e8;
}

.section-title {
  font-size: 14px;
  font-weight: 600;
  color: #262626;
  margin-bottom: 12px;
  padding-bottom: 8px;
  border-bottom: 1px solid #e8e8e8;
}

.form-item {
  margin-bottom: 12px;
}

.form-item label {
  display: block;
  font-size: 12px;
  color: #595959;
  margin-bottom: 6px;
}

/* 逻辑操作符水平排列 */
.form-item.logic-operator {
  display: flex;
  align-items: center;
  gap: 8px;
}

.form-item.logic-operator label {
  display: inline-block;
  margin-bottom: 0;
  white-space: nowrap;
}

.form-item.logic-operator .ant-radio-group {
  display: flex;
  flex-direction: row;
}

.conditions-box {
  max-height: 200px;
  overflow-y: auto;
  border: 1px solid #e8e8e8;
  padding: 8px;
  border-radius: 4px;
  background: #fff;
}

.condition-item {
  padding: 8px;
  background: #f5f5f5;
  border-radius: 4px;
  margin-bottom: 8px;
}

.empty-text {
  text-align: center;
  color: #bfbfbf;
  padding: 16px;
  font-size: 12px;
}

.btn-group {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.search-info {
  padding: 12px;
  background: #f6ffed;
  border: 1px solid #b7eb8f;
  border-radius: 4px;
}

.info-item {
  margin-bottom: 4px;
}

.info-item .label {
  color: #595959;
  font-size: 12px;
}

.info-item .value {
  font-size: 12px;
  margin-left: 4px;
}

.info-item .value.success {
  color: #52c41a;
}

.info-item .value.info {
  color: #1890ff;
}

.selected-preview {
  margin: 8px 0;
  padding: 8px;
  background: #f5f5f5;
  border-radius: 4px;
}

.selected-preview label {
  font-size: 12px;
  color: #595959;
}

.selected-preview .tags {
  margin-top: 4px;
}

/* 中间图谱 */
.center-panel {
  flex: 1;
  min-width: 0;
  background: #fff;
  border-radius: 8px;
  position: relative;
  box-shadow: 0 2px 8px rgba(0,0,0,0.06);
  overflow: hidden;
}

.chart-container {
  width: 100%;
  height: 100%;
  position: relative;
}

/* 空状态覆盖层样式 */
.empty-chart-overlay {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
  background: #fafafa;
  border-radius: 8px;
  border: 2px dashed #d9d9d9;
  z-index: 5;
}

.empty-chart-overlay .empty-title {
  font-size: 18px;
  font-weight: 500;
  color: #262626;
  margin-top: 24px;
  margin-bottom: 8px;
}

.empty-chart-overlay .empty-desc {
  font-size: 14px;
  color: #8c8c8c;
}

.chart-toolbar {
  position: absolute;
  top: 16px;
  right: 16px;
  z-index: 10;
}

.node-status {
  position: absolute;
  bottom: 16px;
  left: 50%;
  transform: translateX(-50%);
  z-index: 10;
}

/* 右侧面板 */
.right-panel {
  width: 380px;
  min-width: 380px;
  background: #fff;
  border-radius: 8px;
  display: flex;
  flex-direction: column;
  box-shadow: 0 2px 8px rgba(0,0,0,0.06);
  animation: slideIn 0.3s ease;
}

@keyframes slideIn {
  from {
    transform: translateX(100%);
    opacity: 0;
  }
  to {
    transform: translateX(0);
    opacity: 1;
  }
}

.panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 20px;
  background: #096dd9;
  color: #fff;
  font-size: 15px;
  font-weight: 600;
}

.panel-content {
  flex: 1;
  padding: 20px;
  overflow-y: auto;
}

.style-settings {
  padding: 12px;
  background: #fafafa;
  border-radius: 4px;
  border: 1px solid #e8e8e8;
}

.style-item {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}

.style-item span {
  width: 50px;
  color: #595959;
  font-size: 13px;
}

/* 图分析结果样式 - 右侧面板内 */
.stat-item {
  display: flex;
  justify-content: space-between;
  padding: 12px 0;
  border-bottom: 1px solid #f0f0f0;
}

.stat-item .label {
  color: #595959;
  font-size: 14px;
}

.stat-item .value {
  font-weight: 600;
  color: #096dd9;
  font-size: 14px;
}

.rank-item {
  display: flex;
  align-items: center;
  padding: 10px 0;
  border-bottom: 1px solid #f0f0f0;
}

.rank-item .rank {
  width: 28px;
  height: 28px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #096dd9;
  color: #fff;
  border-radius: 50%;
  font-size: 13px;
  font-weight: 600;
  margin-right: 12px;
}

.rank-item .name {
  flex: 1;
  font-size: 14px;
}

.rank-item .score {
  font-weight: 600;
  color: #096dd9;
}

.community-item {
  margin-bottom: 16px;
  padding: 12px;
  background: #fafafa;
  border-radius: 6px;
  border: 1px solid #e8e8e8;
}

.community-header {
  display: flex;
  justify-content: space-between;
  margin-bottom: 10px;
}

.community-name {
  font-weight: 600;
  color: #262626;
}

.community-size {
  font-size: 12px;
  color: #8c8c8c;
}

.community-nodes {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.neighbor-list {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 10px;
}

.color-picker {
  width: 40px;
  height: 24px;
  border: 1px solid #d9d9d9;
  border-radius: 4px;
  cursor: pointer;
}

/* 图分析样式 */
.analysis-item {
  margin-bottom: 16px;
  padding: 12px;
  background: #f5f5f5;
  border-radius: 6px;
  border: 1px solid #e8e8e8;
}

.analysis-item label {
  display: block;
  font-size: 12px;
  font-weight: 600;
  color: #262626;
  margin-bottom: 8px;
}

/* 按钮样式 */
:deep(.ant-btn-primary) {
  background: #096dd9;
  border-color: #096dd9;
}

:deep(.ant-btn-primary:hover) {
  background: #1890ff;
  border-color: #1890ff;
}

:deep(.ant-btn-dangerous) {
  background: #fff;
  border-color: #ff4d4f;
  color: #ff4d4f;
}

:deep(.ant-btn-dangerous:hover) {
  background: #ff4d4f;
  border-color: #ff4d4f;
  color: #fff;
}

/* 滚动条 */
.left-panel::-webkit-scrollbar,
.panel-content::-webkit-scrollbar {
  width: 6px;
}

.left-panel::-webkit-scrollbar-thumb,
.panel-content::-webkit-scrollbar-thumb {
  background: #bfbfbf;
  border-radius: 3px;
}

.left-panel::-webkit-scrollbar-thumb:hover,
.panel-content::-webkit-scrollbar-thumb:hover {
  background: #096dd9;
}

/* 响应式 */
@media (max-width: 1400px) {
  .left-panel {
    width: 240px;
    min-width: 240px;
  }
  .right-panel {
    width: 340px;
    min-width: 340px;
  }
}

@media (max-width: 1200px) {
  .left-panel {
    width: 220px;
    min-width: 220px;
  }
  .right-panel {
    width: 300px;
    min-width: 300px;
  }
}

@media (max-width: 992px) {
  .main-container {
    flex-direction: column;
  }
  .left-panel, .right-panel {
    width: 100%;
    min-width: auto;
    max-height: 350px;
  }
}

/* 实体扩线结果样式 */
.expand-levels {
  max-height: 400px;
  overflow-y: auto;
}

.expand-level-section {
  margin-bottom: 12px;
  padding: 10px;
  background: #fafafa;
  border-radius: 6px;
  border: 1px solid #e8e8e8;
}

.expand-level-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
  padding-bottom: 6px;
  border-bottom: 1px dashed #d9d9d9;
}

.expand-level-title {
  font-weight: 600;
  color: #262626;
  font-size: 13px;
}

.expand-level-nodes {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.expand-node-tag {
  margin-bottom: 4px;
}

.more-nodes {
  color: #8c8c8c;
  font-size: 12px;
  padding: 2px 6px;
}

/* 关联查询结果样式 */
.association-list {
  max-height: 300px;
  overflow-y: auto;
}

.association-item {
  margin-bottom: 12px;
  padding: 10px;
  background: #f6ffed;
  border-radius: 6px;
  border: 1px solid #b7eb8f;
}

.association-header {
  display: flex;
  justify-content: space-between;
  margin-bottom: 8px;
}

.association-title {
  font-weight: 600;
  color: #389e0d;
}

.association-nodes {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 4px;
}

.empty-text {
  text-align: center;
  color: #8c8c8c;
  padding: 16px;
  font-size: 14px;
}

/* 模式匹配结果样式 */
.match-list {
  max-height: 300px;
  overflow-y: auto;
}

.match-item {
  margin-bottom: 12px;
  padding: 10px;
  background: #f9f0ff;
  border-radius: 6px;
  border: 1px solid #d3adf7;
}

.match-header {
  display: flex;
  justify-content: space-between;
  margin-bottom: 8px;
}

.match-title {
  font-weight: 600;
  color: #531dab;
}

.match-size {
  font-size: 12px;
  color: #722ed1;
}

.match-nodes {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.match-more {
  text-align: center;
  color: #8c8c8c;
  font-size: 13px;
  padding: 8px;
}

/* disabled 按钮样式 - 确保文字清晰可见 */
:deep(.ant-btn-primary:disabled) {
  color: #ffffff;
  background: #bfbfbf;
  border-color: #bfbfbf;
  opacity: 0.9;
}

:deep(.ant-btn-primary:disabled span) {
  color: #ffffff;
}

/* 社区统计概览 */
.community-stats-overview {
  background: #f6ffed;
  border: 1px solid #b7eb8f;
  border-radius: 8px;
  padding: 16px;
  margin-bottom: 16px;
}

.community-stats-overview .stat-row {
  display: flex;
  gap: 16px;
  margin-bottom: 12px;
}

.community-stats-overview .stat-row:last-child {
  margin-bottom: 0;
}

.community-stats-overview .stat-box {
  flex: 1;
  text-align: center;
  padding: 12px;
  background: #fff;
  border-radius: 6px;
}

.community-stats-overview .stat-value {
  font-size: 20px;
  font-weight: 600;
  color: #1890ff;
  margin-bottom: 4px;
}

.community-stats-overview .stat-label {
  font-size: 12px;
  color: #595959;
}

/* 社区卡片 */
.communities-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.community-card {
  background: #fff;
  border-radius: 8px;
  padding: 16px;
  border-left: 4px solid #1890ff;
  box-shadow: 0 2px 8px rgba(0,0,0,0.04);
  transition: all 0.3s ease;
}

.community-card:hover {
  box-shadow: 0 4px 12px rgba(0,0,0,0.08);
  transform: translateX(4px);
}

.community-card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.community-title {
  display: flex;
  align-items: center;
  gap: 8px;
}

.community-index {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  border-radius: 50%;
  color: #fff;
  font-size: 12px;
  font-weight: 600;
}

.community-name {
  font-size: 14px;
  font-weight: 600;
  color: #262626;
}

.community-badges {
  display: flex;
  gap: 8px;
}

/* 节点类型分布 */
.community-type-distribution {
  margin-bottom: 12px;
  padding: 12px;
  background: #fafafa;
  border-radius: 6px;
}

.community-type-distribution .type-item {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.community-type-distribution .type-item:last-child {
  margin-bottom: 0;
}

.community-type-distribution .type-label {
  width: 60px;
  font-size: 12px;
  color: #595959;
  flex-shrink: 0;
}

.community-type-distribution :deep(.ant-progress) {
  flex: 1;
  margin-right: 8px;
}

.community-type-distribution .type-count {
  width: 30px;
  text-align: right;
  font-size: 12px;
  color: #262626;
  font-weight: 500;
}

/* 社区节点列表 */
.community-nodes-list {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: 12px;
  padding: 12px;
  background: #f5f5f5;
  border-radius: 6px;
}

.community-node-tag {
  margin-right: 0;
}

.more-nodes-tag {
  background: #f0f0f0;
  border: 1px dashed #d9d9d9;
  color: #8c8c8c;
}

/* 社区操作按钮 */
.community-actions {
  display: flex;
  gap: 12px;
  padding-top: 8px;
  border-top: 1px solid #f0f0f0;
}
</style>
