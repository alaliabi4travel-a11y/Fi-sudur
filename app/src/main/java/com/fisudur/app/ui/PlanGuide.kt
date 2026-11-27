package com.fisudur.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

private class Topic(val title: String, val body: String)

private val topics = listOf(
    Topic(
        "فكرة الخطة",
        "تحفظ كل يوم قدرًا ثابتًا: وجهًا أو وجهين أو ثلاثة. ومع الجديد ثلاث طبقات تحفظ ما قبله:\n\n" +
            "١. تكرار الأمس: ما حفظته أمس.\n" +
            "٢. الربط: العشرون وجهًا (جزء) التي قبل الأمس.\n" +
            "٣. المراجعة: ما هو أقدم من ذلك، جزء كل يوم يسير بلا توقف، فإذا وصل إلى الربط عاد إلى أول المصحف.\n\n" +
            "فلا يزيد وردك أبدًا على: الجديد، والأمس، وجزء ربط، وجزء مراجعة، حتى الختم. والتطبيق يحسب كل ذلك وحده.",
    ),
    Topic(
        "الحفظ الجديد",
        "خطوات الأوجه الجديدة بالترتيب:\n\n" +
            "١. اقرأ التفسير مرة واحدة، لتفهم المعنى قبل الحفظ.\n" +
            "٢. استمع إلى القارئ ٣ مرات وأنت تنظر في المصحف، لتضبط النطق.\n" +
            "٣. احفظ الورد حتى تقرأه غيبًا.\n" +
            "٤. سجّل غيبًا لتأكيد الحفظ: ٣ مرات متتالية بلا خطأ.\n" +
            "٥. كرّر لتثبيت الحفظ: ٤٠ مرة (أو ٣٠ لمن يحفظ ثلاثة أوجه)، بالعداد داخل التطبيق. تظهر علامة الصح حين يكتمل العدد.",
    ),
    Topic(
        "تكرار الأمس",
        "تقرأ ما حفظته أمس ٥ مرات غيبًا، قبل أن تبدأ بالجديد. اضغط البطاقة بعد كل قراءة، ويقف العدّ عند ٥. وإن أخطأت في العدّ فاضغط الدائرة بجانبها ليعود من الصفر.\n\n" +
            "هذا أهم ما يثبّت الحفظ، لأن الأوجه تكون في اليوم التالي أسرع ما تكون إلى النسيان.\n\n" +
            "إذا كان اليوم إجازة وكان أمس يوم حفظ، يبقى تكرار الأمس في خطوات الإجازة.",
    ),
    Topic(
        "الربط",
        "الربط هو العشرون وجهًا (جزء واحد) التي قبل حفظ الأمس مباشرة، تقرؤها مرة واحدة غيبًا كل يوم.\n\n" +
            "يتحرك مع حفظك: كل يوم يدخل فيه ما كان حفظ أمس، ويخرج منه أقدم وجه إلى المراجعة.\n\n" +
            "مثال: إن كنت تحفظ اليوم الوجه ٥٠ (وجهًا يوميًا)، فتكرار الأمس هو ٤٩، والربط من ٢٩ إلى ٤٨.\n\n" +
            "فائدته أن يتصل الجديد بما قبله، فتقرأ السورة متصلة لا قطعًا متفرقة.",
    ),
    Topic(
        "المراجعة",
        "المراجعة لكل ما هو أقدم من الربط، ولو كان وجهًا واحدًا. فلا توجد مراجعة حتى يزيد محفوظك على الأمس والربط.\n\n" +
            "تسير بلا توقف: تأخذ كل يوم جزءًا (٢٠ وجهًا) من حيث وقفت أمس، ولا تدخل في الربط أبدًا.\n\n" +
            "فإذا وصلت إلى ما قبل الربط مباشرة ولم يكتمل جزء اليوم، أكملته من أول المصحف. مثال: الربط يبدأ من الوجه ٥٢، ومراجعتك وصلت إلى ٥١، فتكون مراجعة اليوم الوجه ٥١ ثم ١٩ وجهًا من الفاتحة، وغدًا تكمل من حيث وقفت.\n\n" +
            "إذا كان محفوظك القديم أقل من جزء، تراجعه كله كل يوم.\n\n" +
            "كل مرة تعود فيها المراجعة إلى أول المصحف تبدأ جولة جديدة، ورقمها يظهر في خطوة المراجعة وفي إحصائياتي.\n\n" +
            "المراجعة آخر خطوات اليوم.",
    ),
    Topic(
        "الأوجه الضعيفة",
        "إذا تعثّرت في وجه أثناء الربط أو المراجعة أو تكرار الأمس، اضغط على الخطوة مطولًا واختر الوجه.\n\n" +
            "تظهر خطوة «الأوجه الضعيفة» قبل الحفظ الجديد، تقرأ فيها كل وجه ضعيف ٣ مرات غيبًا. وبعد ٣ أيام تنجزها يُعدّ الوجه ثابتًا ويخرج منها.\n\n" +
            "تجد الأوجه الضعيفة وما بقي لكل منها في إحصائياتي، وتستطيع إخراج أي وجه منها بالضغط عليه.",
    ),
    Topic(
        "التفسير",
        "اقرأ تفسير الأوجه الجديدة قبل حفظها، من «التفسير الميسّر» أو «المختصر في التفسير». كلاهما قصير وواضح.\n\n" +
            "فهم المعنى يجعل الحفظ أسرع وأبقى، ويقلّل الخلط بين الآيات المتشابهة.",
    ),
    Topic(
        "الإجازة والعذر",
        "الإجازة يوم راحة أسبوعي تختاره من الإعدادات، كالجمعة. لا حفظ جديد فيه، لكن الربط والمراجعة يستمران، وتكرار الأمس إن كان أمس يوم حفظ.\n\n" +
            "العذر يوم تتركه لسبب: عمل أو سفر أو مرض. تختاره من زر «اجعل اليوم إجازة لعذر» في الرئيسية، ثم تختار السبب.\n\n" +
            "أيام الإجازة والعذر لا تقدّم الخطة، فلا يضيع عليك شيء منها.",
    ),
    Topic(
        "التفريط وتسجيل الأيام الفائتة",
        "التفريط يوم انتهى دون أن يُسجَّل فيه حفظ ولا عذر.\n\n" +
            "إذا أكملت كل الخطوات ونسيت «إنهاء اليوم»، يسجّله التطبيق حفظًا وحده عند نهاية اليوم.\n\n" +
            "وإذا فاتك يوم وكنت قد حفظت فيه أو كان لك عذر، اضغطه في تقويم «آخر أربعة أسابيع» في إحصائياتي وسجّله.\n\n" +
            "وإن سجّلت يومًا خطأً، فاضغطه في التقويم نفسه لتجعله عذرًا أو تحذف تسجيله. يوم الحفظ يُصحَّح إذا لم يأتِ بعده يوم حفظ، حتى لا تتغير أوجه الأيام التالية.\n\n" +
            "وإن لم تحفظ الجديد في يومك، اضغط «إنهاء اليوم» ثم «أكمل الجديد لاحقًا»: يُسجّل اليوم تأجيلًا، وتبقى أوجهه ليوم الحفظ القادم.",
    ),
    Topic(
        "نهاية اليوم",
        "يومك في التطبيق لا ينتهي عند منتصف الليل، بل عند الوقت الذي تختاره في الإعدادات، مثل ٤ فجرًا. فمن يحفظ بعد العشاء أو في آخر الليل يبقى في يومه نفسه.\n\n" +
            "وإن اخترت وقتًا بعد الظهر، مثل ١٠ مساءً، يُسمّى اليوم بتاريخ نهايته: يوم الجمعة يبدأ الخميس ١٠ مساءً.\n\n" +
            "العدّ التنازلي في الرئيسية يُظهر ما بقي من اليوم. وبعد إنهاء اليوم تظهر خانة «ورد الغد»، فتنظر فيه وتبدأ ببعضه إن شئت.",
    ),
    Topic(
        "تغيير المقدار",
        "تستطيع تغيير المقدار بين وجه ووجهين وثلاثة في أي وقت من الإعدادات.\n\n" +
            "يبدأ المقدار الجديد من اليوم، ويُعاد حساب الربط والمراجعة وموعد الختم وحدها، ولا يتغير شيء مما حفظته قبل.",
    ),
)

/** The plan explained, one topic per row; each opens in a window. */
@Composable
fun PlanGuide() {
    val pal = LocalPalette.current
    var open by rememberSaveable { mutableStateOf(-1) }
    var expanded by rememberSaveable { mutableStateOf(false) }
    Panel(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().clickable { expanded = !expanded }, verticalAlignment = Alignment.CenterVertically) {
            IconBadge(Icons.Outlined.Lightbulb, pal.goldSoft, pal.gold, size = 36.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("شرح الخطة", style = MaterialTheme.typography.titleMedium, color = pal.ink)
                Text("كيف يعمل الحفظ والربط والمراجعة", style = MaterialTheme.typography.bodySmall, color = pal.muted)
            }
            Icon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null, tint = pal.gold)
        }
        if (expanded) Spacer(Modifier.height(6.dp))
        if (expanded) topics.forEachIndexed { i, t ->
            if (i > 0) HorizontalDivider(color = pal.line)
            Row(
                Modifier.fillMaxWidth().clickable { open = i }.padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(t.title, style = MaterialTheme.typography.bodyLarge, color = pal.ink, modifier = Modifier.weight(1f))
                Icon(Icons.Outlined.ChevronLeft, null, tint = pal.muted)
            }
        }
    }
    topics.getOrNull(open)?.let { t ->
        AlertDialog(
            onDismissRequest = { open = -1 },
            title = { Text(t.title) },
            text = {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                    Text(t.body, style = MaterialTheme.typography.bodyLarge, color = pal.ink)
                }
            },
            confirmButton = { TextButton(onClick = { open = -1 }) { Text("تم") } },
        )
    }
}
