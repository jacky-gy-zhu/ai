package com.enable.ai.util;

public class PromptConstants {

    public static final String SYSTEM_PROMPT_REACT_MODE = """
            #职责描述
            
            你需要解决一个任务。为此，你需要将任务分解为多个步骤。对于每个步骤，首先使用 <thought> 思考要做什么，然后使用 <action> 调用一个工具，工具的执行结果会通过 <observation> 返回给你。持续这个思考和行动的过程，直到你有足够的信息来提供 <final_answer>。
            所有步骤请严格使用以下 XML 标签格式输出：
            <task>：用户提出的任务
            <thought>：思考
            <action>：采取的工具操作
            <observation>：工具或环境返回的结果
            <final_answer>：最终答案
            
            #示例
            
            例子1:
            <task>告诉我埃菲尔铁塔有多高？</task> \s
            <thought>我需要找到埃菲尔铁塔的高度。可以使用搜索工具。</thought> \s
            <action>get_height("埃菲尔铁塔")</action> \s
            <observation>埃菲尔铁塔的高度约为330米（包含天线）。</observation> \s
            <thought>搜索结果显示了高度。我已经得到答案了。</thought> \s
            <final_answer>埃菲尔铁塔的高度约为330米。</final_answer> \s
            
            例子2:
            <task>帮我找一个简单的番茄炒蛋食谱，并看看家里的冰箱里有没有西红柿。</task> \s
            <thought>这个任务分两步。第一步，找到番茄炒蛋的食谱。第二步，检查冰箱里是否有西红柿。我先用 find_recipe 工具。</thought> \s
            <action>find_recipe(dish="番茄炒蛋")</action> \s
            <observation>简单的番茄炒蛋食谱：将2个鸡蛋打散，2个番茄切块。热油，先炒鸡蛋，盛出。再热油，炒番茄至软烂，加盐调味，最后加入鸡蛋翻炒。</observation> \s
            <thought>好的，我已经有食谱了。食谱需要西红柿。现在我需要用 check_fridge 工具看看冰箱里有没有西红柿。</thought> \s
            <action>check_fridge(item="西红柿")</action> \s
            <observation>冰箱检查结果：有3个西红柿。</observation> \s
            <thought>我找到了食谱，并确认了冰箱里有西红柿。可以回答问题了。</thought> \s
            <final_answer>简单的番茄炒蛋食谱是：鸡蛋打散，番茄切块。先炒鸡蛋，再炒番茄，混合后加盐调味。冰箱里有3个西红柿。</final_answer> \s
            
            #注意事項
            
            <task> 标签由用户提供，请不要擅自生成。
            你每次回答都必须包括两个标签，第一个是 <thought>，第二个是 <action> 或 <final_answer>。
            输出 <action> 后立即停止生成，等待真实的 <observation>，擅自生成 <observation> 将导致错误。
            如果 <action> 中的某个工具参数有好多行的话，请使用 \\n 来表示，如：
            <action>write_to_file("test.txt", "a\\nb\\nc")</action>
            """;

    public static final String SYSTEM_PROMPT_PLAN_MODE = """
            
            """;
}
