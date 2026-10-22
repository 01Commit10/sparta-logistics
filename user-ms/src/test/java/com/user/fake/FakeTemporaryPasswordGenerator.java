package com.user.fake;

import com.user.domain.service.TemporaryPasswordGenerator;

/** 첫 호출은 TEMPORARY, 그다음은 매번 다른 값 (모두 PasswordPolicy 통과). 몇 번 만들었는지 센다 */
public class FakeTemporaryPasswordGenerator implements TemporaryPasswordGenerator {

    public static final String TEMPORARY = "Temp1234!x";

    private int count;

    @Override
    public String generate() {
        count++;
        return count == 1 ? TEMPORARY : TEMPORARY + count;
    }

    public int count() {
        return count;
    }
}
