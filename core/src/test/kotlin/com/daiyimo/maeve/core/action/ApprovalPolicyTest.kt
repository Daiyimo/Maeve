package com.daiyimo.maeve.core.action

import com.daiyimo.maeve.core.run.Attendance
import com.daiyimo.maeve.core.run.RunOrigin
import com.daiyimo.maeve.core.run.RunProvenance
import org.junit.Assert.assertEquals
import org.junit.Test

class ApprovalPolicyTest {
    @Test fun externalAndIrreversibleActionsAlwaysAsk() {
        for (level in ActionRisk.entries) {
            assertEquals("$level", if (level.outward) Approval.Ask else Approval.Allow, ApprovalPolicy.decide(Risk(level)))
            assertEquals("$level payment", Approval.Ask, ApprovalPolicy.decide(Risk(level, payment = true)))
        }
    }

    @Test fun everyOriginAndAttendance() {
        for (origin in RunOrigin.entries) for (attendance in Attendance.entries) for (level in ActionRisk.entries) {
            val run = RunProvenance(origin, attendance)
            // 不花钱的:谁发起、人在不在都直接做
            // 花钱的:只有手机上发起、人在场时问;问不到的(渠道、定时、MCP、子任务、无人值守)不做
            val expected = if (origin == RunOrigin.Ui && attendance != Attendance.Unattended) Approval.Ask else Approval.Deny
            assertEquals("$origin $attendance $level", if (level.outward) expected else Approval.Allow, ApprovalPolicy.decide(Risk(level), run))
            assertEquals("$origin $attendance $level payment", expected, ApprovalPolicy.decide(Risk(level, payment = true), run))
        }
    }

    @Test fun amooMayAskLocallyForAnOrdinaryWriteWithoutChangingOtherRemotePolicy() {
        val amoo = RunProvenance(RunOrigin.Amoo, Attendance.Reachable)
        val mcp = RunProvenance(RunOrigin.Mcp, Attendance.Reachable)
        assertEquals(Approval.Ask, ApprovalPolicy.decide(Risk(ActionRisk.Change), amoo, everyWrite = true))
        assertEquals(Approval.Deny, ApprovalPolicy.decide(Risk(ActionRisk.Change), mcp, everyWrite = true))
        assertEquals(Approval.Deny, ApprovalPolicy.decide(Risk(ActionRisk.External), amoo, everyWrite = true))
        assertEquals(Approval.Deny, ApprovalPolicy.decide(Risk(ActionRisk.Change, payment = true), amoo, everyWrite = true))
        assertEquals(Approval.Deny, ApprovalPolicy.decide(Risk(ActionRisk.Change), amoo.copy(attendance = Attendance.Unattended), everyWrite = true))
    }
}
