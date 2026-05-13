package com.x500x.cursimple.core.plugin.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EamsScheduleParserTest {
    private val parser = EamsScheduleParser()

    @Test
    fun `extractMetadata parses semester ids project and maxWeek`() {
        val pageHtml = """
            <select id="startWeek" name="startWeek">
                <option value="">全部</option>
                <option value="1">第1周</option>
                <option value="2">第2周</option>
                <option value="18">第18周</option>
            </select>
            <script type="text/javascript">
                jQuery("#semesterBar15031397811Semester").semesterCalendar({empty:"false",onChange:"",value:"389"},"searchTable()");
                function searchTable(){
                    if(jQuery("#courseTableType").val()=="std"){
                        bg.form.addInput(form,"ids","556449");
                    }
                }
                var _paramstring = 'ignoreHead=1&setting.kind=std&startWeek=1&project.id=1&semester.id=389&ids=556449';
            </script>
        """.trimIndent()

        val meta = parser.extractMetadata(pageHtml)

        assertEquals("389", meta.semesterId)
        assertEquals("556449", meta.ids)
        assertEquals("1", meta.projectId)
        assertEquals(18, meta.maxWeek)
    }

    @Test
    fun `buildSchedule parses activity spans and valid weeks`() {
        val detailHtml = """
            <script language="JavaScript">
                var table0 = new CourseTable(2026,56);
                var unitCount = 8;
                var teachers = [{id:7343,name:"黄晓娟",lab:false}];
                var actTeachers = [{id:7343,name:"黄晓娟",lab:false}];
                var assistantName = "";
                var actTeacherId = [];
                var actTeacherName = [];
                activity = new TaskActivity(actTeacherId.join(','),actTeacherName.join(','),"111427(776246)","高等数学A（下）(776246)","497","西7-402c","011111000",null,null,assistantName,"","");
                index =0*unitCount+0;
                table0.activities[index][table0.activities[index].length]=activity;
                index =0*unitCount+1;
                table0.activities[index][table0.activities[index].length]=activity;

                var teachers = [{id:10044,name:"徐阳",lab:false}];
                var actTeachers = [{id:10044,name:"徐阳",lab:false}];
                var assistantName = "";
                var actTeacherId = [];
                var actTeacherName = [];
                activity = new TaskActivity(actTeacherId.join(','),actTeacherName.join(','),"126996(776318)","人工智能概论(776318)","433","西5-215c","010001000",null,null,assistantName,"","");
                index =2*unitCount+3;
                table0.activities[index][table0.activities[index].length]=activity;
                table0.marshalTable(2,1,1);
            </script>
            <table id="grid12042826911">
              <tbody id="grid12042826911_data">
                <tr><td>1</td><td>01081TS002</td><td>高等数学A（下）</td><td>5.5</td><td><a href="/eams/courseTableForStd!taskTable.action?lesson.id=3184581">776246</a></td><td>黄晓娟</td></tr>
                <tr><td>2</td><td>01131TS001</td><td>人工智能概论</td><td>1</td><td><a href="/eams/courseTableForStd!taskTable.action?lesson.id=3184653">776318</a></td><td>徐阳</td></tr>
              </tbody>
            </table>
        """.trimIndent()
        val meta = EamsCourseTableMeta(
            semesterId = "389",
            ids = "556449",
            projectId = "1",
            maxWeek = 7,
        )

        val schedule = parser.buildSchedule(
            meta = meta,
            detailHtml = detailHtml,
            termId = "2026-spring",
            updatedAt = "2026-04-30T00:00:00+08:00",
        )

        assertEquals("2026-spring", schedule.termId)
        assertEquals(2, schedule.dailySchedules.size)

        val mondayCourse = schedule.dailySchedules.first { it.dayOfWeek == 1 }.courses.single()
        assertEquals("高等数学A（下）", mondayCourse.title)
        assertEquals("黄晓娟", mondayCourse.teacher)
        assertEquals("西7-402c", mondayCourse.location)
        assertEquals(1, mondayCourse.time.startNode)
        assertEquals(2, mondayCourse.time.endNode)
        assertEquals(listOf(1, 2, 3, 4, 5), mondayCourse.weeks)

        val wednesdayCourse = schedule.dailySchedules.first { it.dayOfWeek == 3 }.courses.single()
        assertEquals("人工智能概论", wednesdayCourse.title)
        assertEquals(5, wednesdayCourse.time.startNode)
        assertEquals(5, wednesdayCourse.time.endNode)
        assertTrue(wednesdayCourse.weeks.containsAll(listOf(1, 5)))
    }

    @Test
    fun `buildSchedule maps table row labels to canonical EAMS nodes`() {
        val detailHtml = """
            <script language="JavaScript">
                var table0 = new CourseTable(2026,56);
                var unitCount = 8;
                var teachers = [{id:7343,name:"黄晓娟",lab:false}];
                var actTeachers = [{id:7343,name:"黄晓娟",lab:false}];
                var assistantName = "";
                var actTeacherId = [];
                var actTeacherName = [];
                activity = new TaskActivity(actTeacherId.join(','),actTeacherName.join(','),"111427(776246)","高等数学A（下）(776246)","497","西7-402c","011111111",null,null,assistantName,"","");
                index =0*unitCount+2;
                table0.activities[index][table0.activities[index].length]=activity;

                var teachers = [{id:10044,name:"徐阳",lab:false}];
                var actTeachers = [{id:10044,name:"徐阳",lab:false}];
                var assistantName = "";
                var actTeacherId = [];
                var actTeacherName = [];
                activity = new TaskActivity(actTeacherId.join(','),actTeacherName.join(','),"126996(776318)","人工智能概论(776318)","433","西5-215c","111111111",null,null,assistantName,"","");
                index =0*unitCount+6;
                table0.activities[index][table0.activities[index].length]=activity;
                table0.marshalTable(2,1,1);
            </script>
            <table id="manualArrangeCourseTable">
              <tr>
                <td><font size="2px"> 第三节</font></td>
                <td id="TD2_0"></td>
              </tr>
              <tr>
                <td><font size="2px"> 午间课</font></td>
                <td id="TD6_0"></td>
              </tr>
            </table>
        """.trimIndent()
        val meta = EamsCourseTableMeta(
            semesterId = "389",
            ids = "556449",
            projectId = "1",
            maxWeek = 7,
        )

        val schedule = parser.buildSchedule(
            meta = meta,
            detailHtml = detailHtml,
            termId = "2026-spring",
            updatedAt = "2026-04-30T00:00:00+08:00",
        )

        val mondayCourses = schedule.dailySchedules.first { it.dayOfWeek == 1 }.courses
        assertEquals(2, mondayCourses.size)
        assertEquals(4, mondayCourses.first { it.title == "高等数学A（下）" }.time.startNode)
        assertEquals(3, mondayCourses.first { it.title == "人工智能概论" }.time.startNode)
    }

    @Test
    fun `buildSchedule deduplicates repeated weekly fragments`() {
        val repeatedActivity = """
            <script>
                var unitCount = 8;
                var teachers = [{id:6816,name:"刘静S2",lab:false}];
                var actTeacherId = [];
                var actTeacherName = [];
                var assistantName = "";
                activity = new TaskActivity(actTeacherId.join(','),actTeacherName.join(','),"136129(780240)","综合英语（发展）2(780240)","457","西6-307c","0110",null,null,assistantName,"","");
                index =1*unitCount+1;
                table0.activities[index][table0.activities[index].length]=activity;
            </script>
        """.trimIndent()
        val laterOnlyActivity = """
            <script>
                var unitCount = 8;
                var teachers = [{id:8888,name:"李港",lab:false}];
                var actTeacherId = [];
                var actTeacherName = [];
                var assistantName = "";
                activity = new TaskActivity(actTeacherId.join(','),actTeacherName.join(','),"02082(776259)","心理健康教育(776259)","457","西6-308c","0010",null,null,assistantName,"","");
                index =2*unitCount+3;
                table0.activities[index][table0.activities[index].length]=activity;
            </script>
        """.trimIndent()
        val meta = EamsCourseTableMeta(
            semesterId = "389",
            ids = "556449",
            projectId = "1",
            maxWeek = 3,
        )

        val schedule = parser.buildSchedule(
            meta = meta,
            detailHtml = "$repeatedActivity\n$repeatedActivity\n$laterOnlyActivity",
            termId = "389",
            updatedAt = "2026-04-30T00:00:00+08:00",
        )

        val courses = schedule.dailySchedules.flatMap { it.courses }
        assertEquals(2, courses.size)
        assertTrue(courses.any { it.title == "综合英语（发展）2" && it.weeks == listOf(1, 2) })
        assertTrue(courses.any { it.title == "心理健康教育" && it.weeks == listOf(2) })
    }
}
