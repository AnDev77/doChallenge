package com.fitmeet.auth.presentation;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import com.fitmeet.auth.application.*;
import com.fitmeet.auth.presentation.request.SignupRequest;
import com.fitmeet.common.exception.*;
import com.fitmeet.member.application.MemberService;
import org.junit.jupiter.api.Test;

class SignupProofTest {
    private final MemberService members = mock(MemberService.class);
    private final EmailVerificationService verification = mock(EmailVerificationService.class);
    private final AuthController controller = new AuthController(members, verification, mock(AuthService.class));
    private final SignupRequest request = new SignupRequest("test@example.invalid", "password123", "tester", "a".repeat(43));

    @Test void rejectedProofCannotCreateMember() {
        doThrow(new BaseException(ErrorCode.EMAIL_NOT_VERIFIED)).when(verification)
                .consumeVerifiedEmail(request.email(), request.verificationToken());
        assertThatThrownBy(() -> controller.signup(request)).isInstanceOf(BaseException.class);
        verifyNoInteractions(members);
    }

    @Test void proofIsConsumedBeforeMemberWriteEvenWhenDatabaseFails() {
        when(members.signupVerifiedMember(anyString(), anyString(), anyString()))
                .thenThrow(new IllegalStateException("database unavailable"));
        assertThatThrownBy(() -> controller.signup(request)).isInstanceOf(IllegalStateException.class);
        var order = inOrder(verification, members);
        order.verify(verification).consumeVerifiedEmail(request.email(), request.verificationToken());
        order.verify(members).signupVerifiedMember(request.email(), request.password(), request.nickname());
    }
}
