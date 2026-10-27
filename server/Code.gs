/**
 * Học Nổi - máy chủ Google Apps Script
 * Gửi mã đăng nhập qua email, cấp token, lưu/tải dữ liệu bằng Google Drive.
 * Triển khai: Deploy > New deployment > Web app > Execute as: Me > Who has access: Anyone.
 * Lần đầu: chạy hàm setup() để cấp quyền (Gmail, Drive).
 */
const FOLDER_NAME = 'HocNoi';
const CODE_TTL = 600;   // mã có hiệu lực 10 phút
const COOLDOWN = 60;    // giãn cách gửi mã cho cùng 1 email
const MAX_TRY = 5;      // số lần nhập sai tối đa

function setup() {
  MailApp.getRemainingDailyQuota();
  folder_();
  PropertiesService.getScriptProperties();
  CacheService.getScriptCache();
}

function doGet() { return out_({ ok: true, name: 'Hoc Noi server' }); }

function doPost(e) {
  try {
    const q = JSON.parse(e.postData.contents);
    switch (q.a) {
      case 'send':   return out_(sendCode_(q.email));
      case 'verify': return out_(verify_(q.email, q.code));
      case 'save':   return out_(save_(q.token, q.data));
      case 'load':   return out_(load_(q.token));
      case 'logout': return out_(logout_(q.token));
    }
    return out_(err_('Yêu cầu không hợp lệ.'));
  } catch (ex) {
    return out_(err_('Lỗi máy chủ: ' + ex));
  }
}

function out_(o) {
  return ContentService.createTextOutput(JSON.stringify(o)).setMimeType(ContentService.MimeType.JSON);
}
function err_(m, auth) { return { ok: false, error: m, auth: !!auth }; }
function norm_(s) { return String(s || '').trim().toLowerCase(); }

function hash_(s) {
  return Utilities.computeDigest(Utilities.DigestAlgorithm.SHA_256, s, Utilities.Charset.UTF_8)
    .map(function (b) { return ('0' + (b & 255).toString(16)).slice(-2); }).join('');
}

function folder_() {
  const it = DriveApp.getFoldersByName(FOLDER_NAME);
  return it.hasNext() ? it.next() : DriveApp.createFolder(FOLDER_NAME);
}
function fileName_(email) { return 'u_' + hash_(email) + '.json'; }
function findFile_(email) {
  const it = folder_().getFilesByName(fileName_(email));
  return it.hasNext() ? it.next() : null;
}

function sendCode_(email) {
  email = norm_(email);
  if (!/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(email)) return err_('Email không hợp lệ.');
  const cache = CacheService.getScriptCache();
  if (cache.get('cd:' + email)) return err_('Vui lòng đợi ' + COOLDOWN + ' giây rồi gửi lại.');
  if (MailApp.getRemainingDailyQuota() < 1) return err_('Máy chủ đã hết hạn mức gửi email hôm nay, hãy thử lại vào ngày mai.');

  const b = Utilities.computeDigest(Utilities.DigestAlgorithm.SHA_256, Utilities.getUuid());
  let n = 0;
  for (let i = 0; i < 4; i++) n = n * 256 + (b[i] & 255);
  const code = String(100000 + (n % 900000));

  cache.put('code:' + email, hash_(code + ':' + email), CODE_TTL);
  cache.put('try:' + email, '0', CODE_TTL);
  cache.put('cd:' + email, '1', COOLDOWN);
  MailApp.sendEmail({
    to: email,
    name: 'Học Nổi',
    subject: 'Mã đăng nhập Học Nổi: ' + code,
    body: 'Mã đăng nhập Học Nổi của bạn là: ' + code + '\n\nMã có hiệu lực 10 phút. Nếu không phải bạn yêu cầu, hãy bỏ qua email này.'
  });
  return { ok: true };
}

function verify_(email, code) {
  email = norm_(email);
  const cache = CacheService.getScriptCache();
  const h = cache.get('code:' + email);
  if (!h) return err_('Mã đã hết hạn hoặc chưa được gửi.');
  const tries = parseInt(cache.get('try:' + email) || '0', 10) + 1;
  if (tries > MAX_TRY) {
    cache.remove('code:' + email);
    return err_('Nhập sai quá nhiều lần, hãy gửi mã mới.');
  }
  cache.put('try:' + email, String(tries), CODE_TTL);
  if (hash_(String(code || '').trim() + ':' + email) !== h) return err_('Mã không đúng.');
  cache.remove('code:' + email);
  const token = (Utilities.getUuid() + Utilities.getUuid()).replace(/-/g, '');
  PropertiesService.getScriptProperties().setProperty('t:' + hash_(token), email);
  return { ok: true, token: token };
}

function emailOf_(token) {
  if (!token) return null;
  return PropertiesService.getScriptProperties().getProperty('t:' + hash_(String(token)));
}
const NEED_LOGIN = 'Phiên đăng nhập hết hạn, hãy đăng nhập lại.';

function save_(token, data) {
  const email = emailOf_(token);
  if (!email) return err_(NEED_LOGIN, true);
  if (!data || typeof data !== 'object') return err_('Thiếu dữ liệu.');
  const lock = LockService.getScriptLock();
  lock.waitLock(20000);
  try {
    const file = findFile_(email);
    let old = null;
    if (file) { try { old = JSON.parse(file.getBlob().getDataAsString()); } catch (x) {} }
    // Không gửi ảnh thì giữ nguyên ảnh cũ trên Drive
    if (data.images === undefined && old && old.images) data.images = old.images;
    data.updated = new Date().toISOString();
    const text = JSON.stringify(data);
    if (file) file.setContent(text);
    else folder_().createFile(fileName_(email), text, MimeType.PLAIN_TEXT);
  } finally {
    lock.releaseLock();
  }
  return { ok: true };
}

function load_(token) {
  const email = emailOf_(token);
  if (!email) return err_(NEED_LOGIN, true);
  const file = findFile_(email);
  if (!file) return { ok: true, data: null };
  return { ok: true, data: JSON.parse(file.getBlob().getDataAsString()) };
}

function logout_(token) {
  if (token) PropertiesService.getScriptProperties().deleteProperty('t:' + hash_(String(token)));
  return { ok: true };
}
