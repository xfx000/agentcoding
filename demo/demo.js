"use strict";
// Loaded only by the static demo build. No model gateway or backend is contacted.
window.qiqiDemoFetch = async function (url, options = {}) {
  if (url === "/api/meta")
    return new Response(
      JSON.stringify({
        modelConfigured: true,
        demoUsers: [
          { username: "demo", displayName: "演示访客", dataScope: "ALL" },
        ],
      }),
      { headers: { "Content-Type": "application/json" } },
    );
  if (url !== "/api/chat/stream") throw new Error("演示版不访问外部接口");
  const query = JSON.parse(options.body).query;
  const report = /表结构|字段|数据表/.test(query)
    ? `## 探索示例数据\n\n这是 **固定演示内容**，未连接数据库。\n\n| 数据表 | 用途 | 关键字段 |\n| --- | --- | --- |\n| department | 部门 | id、name |\n| customer | 客户 | id、department_id |\n| product | 商品 | id、name、category |\n| sales_order | 订单 | id、order_date、status、total_amount |\n| sales_order_item | 订单明细 | order_id、product_id、quantity |\n\n### 可以怎样分析？\n\n- 比较不同时段的收入变化。\n- 按部门查看业务贡献。\n- 对商品品类进行销售排行。\n\n> 页面用于体验交互；完整版本才会根据实际数据库结构生成分析。`
    : /品类|畅销|销量/.test(query)
      ? `## 商品品类销售表现\n\n以下为 **预设的模拟数据**，不是实时查询结果。\n\n| 品类 | 已付款销售额 | 销量 | 销售占比 |\n| --- | ---: | ---: | ---: |\n| 数码配件 | ¥18,600 | 124 | 55.03% |\n| 办公用品 | ¥9,200 | 230 | 27.22% |\n| 生活用品 | ¥6,000 | 150 | 17.75% |\n| 合计 | **¥33,800** | **504** | **100%** |\n\n### 示例解读\n\n**数码配件**贡献过半销售额；办公用品销量最高，可以进一步观察客单价与复购。\n\n### SQL 展示\n\n下面仅展示 SQL 格式，演示版不会执行：\n\n\`\`\`sql\nSELECT category, SUM(revenue) AS sales\nFROM demo_category_sales\nGROUP BY category\nORDER BY sales DESC;\n\`\`\`\n\n可以体验复制、下载报告、切换主题，以及新建多个对话。`
      : `## 月度收入对比\n\n这是 **固定的收入分析示例**，用于体验流式报告。你的问题不会发送到模型或服务器。\n\n### 收入概览\n\n| 部门 | 2026 年 1 月 | 2026 年 2 月 | 增长金额 | 增长率 |\n| --- | ---: | ---: | ---: | ---: |\n| North Sales | ¥7,100 | ¥9,100 | +¥2,000 | +28.17% |\n| South Sales | ¥8,300 | ¥9,300 | +¥1,000 | +12.05% |\n| 合计 | **¥15,400** | **¥18,400** | **+¥3,000** | **+19.48%** |\n\n### 示例解读\n\n1. 模拟数据中，2 月收入增长 **19.48%**。\n2. North Sales 贡献约 **66.7%** 的增长金额。\n3. 两部门均有增长，可以继续从品类与订单数量观察变化。\n\n### 查询语句示例\n\n\`\`\`sql\nSELECT department, jan_revenue, feb_revenue,\n       feb_revenue - jan_revenue AS growth\nFROM demo_monthly_revenue;\n\`\`\`\n\n> SQL 和数字均用于展示，未执行实际查询。\n\n试试左侧 **新建分析**，或在 **设置** 中切换雾青绿主题与打字速度。`;
  const chunks = Array.from(report);
  const encoder = new TextEncoder();
  let timer;
  let ended = false;
  let abort;
  const body = new ReadableStream({
    start(controller) {
      const emit = (type, data = {}) =>
        controller.enqueue(
          encoder.encode(`data: ${JSON.stringify({ type, data })}\n\n`),
        );
      abort = () => {
        if (ended) return;
        ended = true;
        clearTimeout(timer);
        controller.error(new DOMException("已停止", "AbortError"));
        options.signal?.removeEventListener("abort", abort);
      };
      options.signal?.addEventListener("abort", abort, { once: true });
      if (options.signal?.aborted) {
        abort();
        return;
      }
      let offset = 0;
      const tick = () => {
        if (ended) return;
        if (offset < chunks.length) {
          emit("TEXT_BLOCK_DELTA", {
            replyId: "demo",
            delta: chunks.slice(offset, offset + 12).join(""),
          });
          offset += 12;
          timer = setTimeout(tick, 65);
        } else {
          ended = true;
          emit("AGENT_END");
          controller.close();
          options.signal?.removeEventListener("abort", abort);
        }
      };
      timer = setTimeout(tick, 300);
    },
    cancel() {
      ended = true;
      clearTimeout(timer);
      options.signal?.removeEventListener("abort", abort);
    },
  });
  return new Response(body, {
    headers: { "Content-Type": "text/event-stream" },
  });
};
