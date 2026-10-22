package dev.glacia.app

import dev.glacia.app.data.LoginStage
import org.junit.Assert.assertTrue
import org.junit.Test

class Phase2ContractTest {
    @Test fun authStatesRemainDistinct() {
        assertTrue(LoginStage.Code != LoginStage.Phone)
        assertTrue(LoginStage.Ready != LoginStage.Unconfigured)
    }
}
