package com.yt.sso.build;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author sunan
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class LoginState {

    private String from;     // system / customer
    private Long bizId;      // 业务ID
    private Long timestamp; // 防重放

    public static LoginState system() {
        return new LoginState("system", null, System.currentTimeMillis());
    }

    public static LoginState customer() {
        return new LoginState("customer", null, System.currentTimeMillis());
    }
}
