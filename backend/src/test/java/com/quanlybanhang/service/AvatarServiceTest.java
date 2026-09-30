package com.quanlybanhang.service;

import com.quanlybanhang.model.User;
import com.quanlybanhang.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AvatarServiceTest {

    @Mock
    private UserRepository userRepository;

    @TempDir
    Path storageDirectory;

    private AvatarService avatarService;
    private User user;

    @BeforeEach
    void setUp() {
        avatarService = new AvatarService(userRepository);
        ReflectionTestUtils.setField(avatarService, "storagePath", storageDirectory.toString());
        user = User.builder()
                .id(7L)
                .username("sales01")
                .email("sales01@example.com")
                .password("encoded")
                .fullName("Nhân viên Kinh doanh")
                .build();
    }

    @Test
    void savesCenteredSquareAvatarAndThumbnailForTheAuthenticatedUser() throws Exception {
        byte[] source = imageBytes("png", 360, 200);
        MockMultipartFile file = new MockMultipartFile(
                "file", "profile.png", "image/png", source);
        when(userRepository.findByUsername("sales01")).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User saved = avatarService.updateAvatar("sales01", file);

        assertThat(saved.getAvatarFileName()).endsWith(".png");
        assertThat(saved.getAvatarThumbnailFileName()).endsWith("-thumb.png");
        assertThat(saved.getAvatarContentType()).isEqualTo("image/png");
        assertThat(saved.getAvatarUpdatedAt()).isNotNull();
        assertThat(Files.isRegularFile(storageDirectory.resolve(saved.getAvatarFileName()))).isTrue();
        assertThat(Files.isRegularFile(storageDirectory.resolve(saved.getAvatarThumbnailFileName()))).isTrue();

        BufferedImage avatar = ImageIO.read(storageDirectory.resolve(saved.getAvatarFileName()).toFile());
        BufferedImage thumbnail = ImageIO.read(storageDirectory.resolve(saved.getAvatarThumbnailFileName()).toFile());
        assertThat(avatar.getWidth()).isEqualTo(AvatarService.AVATAR_SIZE);
        assertThat(avatar.getHeight()).isEqualTo(AvatarService.AVATAR_SIZE);
        assertThat(thumbnail.getWidth()).isEqualTo(AvatarService.THUMBNAIL_SIZE);
        assertThat(thumbnail.getHeight()).isEqualTo(AvatarService.THUMBNAIL_SIZE);
        verify(userRepository).save(user);
    }

    @Test
    void returnsStoredThumbnailLinkedToTheRequestedUser() throws Exception {
        byte[] source = imageBytes("jpg", 240, 320);
        MockMultipartFile file = new MockMultipartFile(
                "file", "profile.jpeg", "image/jpeg", source);
        when(userRepository.findByUsername("sales01")).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        avatarService.updateAvatar("sales01", file);
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));

        AvatarService.AvatarContent content = avatarService.getAvatar(7L, true).orElseThrow();

        BufferedImage thumbnail = ImageIO.read(new ByteArrayInputStream(content.bytes()));
        assertThat(content.contentType()).isEqualTo("image/jpeg");
        assertThat(thumbnail.getWidth()).isEqualTo(AvatarService.THUMBNAIL_SIZE);
        assertThat(thumbnail.getHeight()).isEqualTo(AvatarService.THUMBNAIL_SIZE);
    }

    @Test
    void rejectsFilesLargerThanTwoMegabytesBeforeLookingUpTheUser() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "large.jpg", "image/jpeg", new byte[(int) AvatarService.MAX_FILE_SIZE + 1]);

        assertThatThrownBy(() -> avatarService.updateAvatar("sales01", file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Ảnh đại diện không được vượt quá 2MB.");
        verify(userRepository, never()).findByUsername(any());
    }

    @Test
    void rejectsADeclaredImageWhenItsActualContentIsInvalid() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "profile.png", "image/png", "not-an-image".getBytes());
        when(userRepository.findByUsername("sales01")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> avatarService.updateAvatar("sales01", file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("File ảnh không hợp lệ.");
        verify(userRepository, never()).save(any());
    }

    private byte[] imageBytes(String format, int width, int height) throws Exception {
        int type = "png".equals(format) ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;
        BufferedImage image = new BufferedImage(width, height, type);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(Color.BLUE);
            graphics.fillRect(0, 0, width / 2, height);
            graphics.setColor(Color.GREEN);
            graphics.fillRect(width / 2, 0, width, height);
        } finally {
            graphics.dispose();
        }
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            ImageIO.write(image, format, output);
            return output.toByteArray();
        }
    }
}
