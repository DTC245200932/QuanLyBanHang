$env:APP_JWT_SECRET="chuoi-bi-mat-rieng-cua-ban-it-nhat-32-ky-tu"
$env:SPRING_DATASOURCE_PASSWORD="mat-khau-mysql-cua-ban"
$env:APP_BOOTSTRAP_ADMIN_PASSWORD="mat-khau-admin-ban-tu-chon"
$env:APP_BOOTSTRAP_ADMIN_EMAIL="email-quan-tri-thuc-te-cua-ban"
$env:MAIL_USERNAME="email-gui-thu-cua-ban"
$env:MAIL_APP_PASSWORD="mat-khau-ung-dung-mail-cua-ban"

cd D:\GitHub\QuanLyBanHang\backend
.\mvnw.cmd spring-boot:run
