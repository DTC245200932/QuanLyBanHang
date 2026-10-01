package com.quanlybanhang.service;

import com.quanlybanhang.model.User;
import com.quanlybanhang.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Iterator;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AvatarService {
    static final long MAX_FILE_SIZE = 2L * 1024 * 1024;
    static final int AVATAR_SIZE = 512;
    static final int THUMBNAIL_SIZE = 96;

    private final UserRepository userRepository;

    @Value("${app.avatar.storage-path:./data/avatars}")
    private String storagePath;

    public User updateAvatar(String username, MultipartFile file) {
        validateFile(file);
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy người dùng"));
        ProcessedAvatar processed = process(file);

        String identifier = user.getId() + "-" + UUID.randomUUID();
        String avatarFileName = identifier + "." + processed.extension();
        String thumbnailFileName = identifier + "-thumb." + processed.extension();
        Path avatarPath = resolve(avatarFileName);
        Path thumbnailPath = resolve(thumbnailFileName);
        String oldAvatar = user.getAvatarFileName();
        String oldThumbnail = user.getAvatarThumbnailFileName();

        try {
            Files.createDirectories(storageRoot());
            Files.write(avatarPath, processed.avatar(), StandardOpenOption.CREATE_NEW);
            Files.write(thumbnailPath, processed.thumbnail(), StandardOpenOption.CREATE_NEW);

            user.setAvatarFileName(avatarFileName);
            user.setAvatarThumbnailFileName(thumbnailFileName);
            user.setAvatarContentType(processed.contentType());
            user.setAvatarUpdatedAt(LocalDateTime.now());
            User saved = userRepository.save(user);

            deleteQuietly(oldAvatar);
            deleteQuietly(oldThumbnail);
            return saved;
        } catch (RuntimeException | IOException ex) {
            deleteQuietly(avatarFileName);
            deleteQuietly(thumbnailFileName);
            if (ex instanceof IllegalArgumentException illegalArgumentException) {
                throw illegalArgumentException;
            }
            throw new IllegalStateException("Không thể lưu ảnh đại diện. Vui lòng thử lại.", ex);
        }
    }

    public Optional<AvatarContent> getAvatar(Long userId, boolean thumbnail) {
        return userRepository.findById(userId).flatMap(user -> {
            String fileName = thumbnail
                    ? user.getAvatarThumbnailFileName()
                    : user.getAvatarFileName();
            if (fileName == null || fileName.isBlank()) {
                return Optional.empty();
            }
            try {
                Path file = resolve(fileName);
                if (!Files.isRegularFile(file)) {
                    return Optional.empty();
                }
                return Optional.of(new AvatarContent(
                        Files.readAllBytes(file), user.getAvatarContentType()));
            } catch (IOException ex) {
                throw new IllegalStateException("Không thể đọc ảnh đại diện.", ex);
            }
        });
    }

    public static String avatarUrl(User user, boolean thumbnail) {
        String fileName = thumbnail
                ? user.getAvatarThumbnailFileName()
                : user.getAvatarFileName();
        if (fileName == null || fileName.isBlank()) {
            return null;
        }
        long version = user.getAvatarUpdatedAt() == null ? 0L
                : user.getAvatarUpdatedAt().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        return "/auth/users/" + user.getId() + "/avatar?thumbnail=" + thumbnail + "&v=" + version;
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Vui lòng chọn ảnh đại diện.");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("Ảnh đại diện không được vượt quá 2MB.");
        }
        String contentType = Optional.ofNullable(file.getContentType())
                .orElse("").toLowerCase(Locale.ROOT);
        if (!contentType.equals("image/jpeg")
                && !contentType.equals("image/jpg")
                && !contentType.equals("image/png")) {
            throw new IllegalArgumentException("Chỉ chấp nhận ảnh JPG, JPEG hoặc PNG.");
        }
    }

    private ProcessedAvatar process(MultipartFile file) {
        try (ImageInputStream input = ImageIO.createImageInputStream(file.getInputStream())) {
            if (input == null) {
                throw new IllegalArgumentException("File ảnh không hợp lệ.");
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw new IllegalArgumentException("File ảnh không hợp lệ.");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                boolean png = "png".equals(format);
                boolean jpeg = "jpeg".equals(format) || "jpg".equals(format);
                if (!png && !jpeg) {
                    throw new IllegalArgumentException("Chỉ chấp nhận ảnh JPG, JPEG hoặc PNG.");
                }
                String declaredType = Optional.ofNullable(file.getContentType())
                        .orElse("").toLowerCase(Locale.ROOT);
                if ((png && !"image/png".equals(declaredType))
                        || (jpeg && !declaredType.equals("image/jpeg")
                        && !declaredType.equals("image/jpg"))) {
                    throw new IllegalArgumentException("Định dạng file không khớp với nội dung ảnh.");
                }
                BufferedImage source = reader.read(0);
                String extension = png ? "png" : "jpg";
                String contentType = png ? "image/png" : "image/jpeg";
                return new ProcessedAvatar(
                        encodeSquare(source, AVATAR_SIZE, extension),
                        encodeSquare(source, THUMBNAIL_SIZE, extension),
                        extension,
                        contentType);
            } finally {
                reader.dispose();
            }
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (IOException ex) {
            throw new IllegalArgumentException("Không thể đọc file ảnh.", ex);
        }
    }

    private byte[] encodeSquare(BufferedImage source, int size, String extension) throws IOException {
        int side = Math.min(source.getWidth(), source.getHeight());
        int sourceX = (source.getWidth() - side) / 2;
        int sourceY = (source.getHeight() - side) / 2;
        int imageType = "png".equals(extension)
                ? BufferedImage.TYPE_INT_ARGB
                : BufferedImage.TYPE_INT_RGB;
        BufferedImage target = new BufferedImage(size, size, imageType);
        Graphics2D graphics = target.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING,
                    RenderingHints.VALUE_RENDER_QUALITY);
            if (!"png".equals(extension)) {
                graphics.setColor(Color.WHITE);
                graphics.fillRect(0, 0, size, size);
            }
            graphics.drawImage(source,
                    0, 0, size, size,
                    sourceX, sourceY, sourceX + side, sourceY + side,
                    null);
        } finally {
            graphics.dispose();
        }

        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            if (!ImageIO.write(target, extension, output)) {
                throw new IOException("Không có bộ mã hoá ảnh phù hợp");
            }
            return output.toByteArray();
        }
    }

    private Path storageRoot() {
        return Path.of(storagePath).toAbsolutePath().normalize();
    }

    private Path resolve(String fileName) {
        Path root = storageRoot();
        Path resolved = root.resolve(fileName).normalize();
        if (!resolved.startsWith(root)) {
            throw new IllegalArgumentException("Tên file ảnh không hợp lệ.");
        }
        return resolved;
    }

    private void deleteQuietly(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return;
        }
        try {
            Files.deleteIfExists(resolve(fileName));
        } catch (IOException ignored) {
            // File cũ không ảnh hưởng đến ảnh vừa cập nhật; có thể dọn sau.
        }
    }

    private record ProcessedAvatar(byte[] avatar, byte[] thumbnail,
                                   String extension, String contentType) {}

    public record AvatarContent(byte[] bytes, String contentType) {}
}
