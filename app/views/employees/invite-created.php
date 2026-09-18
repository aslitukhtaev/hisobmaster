<section class="page-head">
    <h1><?= e(t('invite_link_ready')) ?></h1>
    <p class="muted"><?= e(t('invite_link_hint', ['date' => substr($expiresAt, 0, 16)])) ?></p>
</section>

<div class="card form-card">
    <div class="invite-link-box" id="invite-link-box"><?= e($link) ?></div>
    <button type="button" class="btn btn-primary btn-block" style="margin-top:14px;" onclick="hmCopyInviteLink()">
        <?= e(t('copy_link')) ?>
    </button>
    <a href="/employees" class="btn btn-ghost btn-block" style="margin-top:10px;"><?= e(t('back_to_list')) ?></a>
</div>

<script>
    function hmCopyInviteLink() {
        var text = document.getElementById('invite-link-box').textContent;
        if (navigator.clipboard) {
            navigator.clipboard.writeText(text);
        }
    }
</script>
