package net.sharplab.springframework.security.webauthn.sample.domain.component;

import com.webauthn4j.util.Base64UrlUtil;
import net.sharplab.springframework.security.webauthn.exception.CredentialIdNotFoundException;
import net.sharplab.springframework.security.webauthn.sample.domain.constant.MessageCodes;
import net.sharplab.springframework.security.webauthn.sample.domain.entity.AuthenticatorEntity;
import net.sharplab.springframework.security.webauthn.sample.domain.entity.UserEntity;
import net.sharplab.springframework.security.webauthn.sample.domain.exception.WebAuthnSampleBusinessException;
import net.sharplab.springframework.security.webauthn.sample.domain.exception.WebAuthnSampleEntityNotFoundException;
import net.sharplab.springframework.security.webauthn.sample.domain.repository.AuthenticatorEntityRepository;
import net.sharplab.springframework.security.webauthn.sample.domain.repository.UserEntityRepository;
import net.sharplab.springframework.security.webauthn.userdetails.WebAuthnUserDetails;
import net.sharplab.springframework.security.webauthn.userdetails.WebAuthnUserDetailsService;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.terasoluna.gfw.common.message.ResultMessages;

/**
 * {@inheritDoc}
 */
@Component
@Transactional
public class UserManagerImpl implements UserManager, WebAuthnUserDetailsService {

    private ModelMapper modelMapper;

    private UserEntityRepository userEntityRepository;
    private AuthenticatorEntityRepository authenticatorEntityRepository;

    @Autowired
    public UserManagerImpl(ModelMapper mapper, UserEntityRepository userEntityRepository, AuthenticatorEntityRepository authenticatorEntityRepository) {
        this.modelMapper = mapper;
        this.userEntityRepository = userEntityRepository;
        this.authenticatorEntityRepository = authenticatorEntityRepository;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public UserEntity findById(int id) {
        return userEntityRepository.findById(id)
                .orElseThrow(() -> new WebAuthnSampleEntityNotFoundException(ResultMessages.error().add(MessageCodes.Error.User.USER_NOT_FOUND)));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public WebAuthnUserDetails loadUserByUsername(String username) {
        return userEntityRepository.findOneByEmailAddress(username)
                .orElseThrow(() -> new UsernameNotFoundException(String.format("UserEntity with username'%s' is not found.", username)));
    }

    @Override
    public WebAuthnUserDetails loadUserByCredentialId(byte[] credentialId) {
        AuthenticatorEntity authenticatorEntity = authenticatorEntityRepository.findOneByCredentialId(credentialId)
                .orElseThrow(()-> new CredentialIdNotFoundException(String.format("AuthenticatorEntity with credentialId'%s' is not found.", Base64UrlUtil.encodeToString(credentialId))));
        return authenticatorEntity.getUser();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public UserEntity createUser(UserEntity user) {
        userEntityRepository.findOneByEmailAddress(user.getEmailAddress()).ifPresent((retrievedUserEntity) -> {
            throw new WebAuthnSampleBusinessException(ResultMessages.error().add(MessageCodes.Error.User.EMAIL_ADDRESS_IS_ALREADY_USED));
        });
        return userEntityRepository.save(user);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void updateUser(UserEntity user) {

        UserEntity userEntity = userEntityRepository.findById(user.getId())
                .orElseThrow(() -> new WebAuthnSampleEntityNotFoundException(ResultMessages.error().add(MessageCodes.Error.User.USER_NOT_FOUND)));
        userEntityRepository.save(userEntity);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deleteUser(String username) {
        UserEntity userEntity = userEntityRepository.findOneByEmailAddress(username)
                .orElseThrow(() -> new UsernameNotFoundException(String.format("UserEntity with username'%s' is not found.", username)));
        userEntityRepository.delete(userEntity);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deleteUser(int id) {
        userEntityRepository.findById(id)
                .orElseThrow(() -> new WebAuthnSampleEntityNotFoundException(ResultMessages.error().add(MessageCodes.Error.User.USER_NOT_FOUND)));
        userEntityRepository.deleteById(id);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void changePassword(String oldPassword, String newPassword) {
        UserEntity currentUserEntity = getCurrentUser();

        if (currentUserEntity == null) {
            // This would indicate bad coding somewhere
            throw new AccessDeniedException(
                    "Can't change rawPassword as no Authentication object found in context "
                            + "for current user.");
        }

        currentUserEntity.setPassword(newPassword);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean userExists(String username) {
        return userEntityRepository.findOneByEmailAddress(username).isPresent();
    }

    /**
     * 現在のユーザーを返却する
     *
     * @return ユーザー
     */
    private UserEntity getCurrentUser() {
        return (UserEntity) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
}