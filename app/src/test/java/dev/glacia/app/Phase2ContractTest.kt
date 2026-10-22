package dev.glacia.app

import dev.glacia.app.data.LoginStage
import org.junit.Assert.assertNotEquals
import org.junit.Test

class Phase2ContractTest {
    @Test fun authStatesRemainDistinct() {
        assertNotEquals(LoginStage.Code, LoginStage.Phone)
        assertNotEquals(LoginStage.Ready, LoginStage.Unconfigured)
    }
}
