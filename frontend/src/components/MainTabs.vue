<template>
  <nav
    class="tabs space-x-1 overflow-x-auto"
    aria-label="页面标签"
    role="tablist"
    aria-orientation="horizontal"
  >
    <button
      type="button"
      class="grow btn btn-text active-tab:bg-primary active-tab:text-white hover:text-primary active hover:bg-primary/20"
      id="tabs-main-workflow-item"
      data-tab="#tabs-main-workflow"
      aria-controls="tabs-main-workflow"
      role="tab"
      aria-selected="true"
    >
      工作流
    </button>
    <button
      type="button"
      class="grow btn btn-text active-tab:bg-primary active-tab:text-white hover:text-primary hover:bg-primary/20"
      id="tabs-main-images-item"
      data-tab="#tabs-main-images"
      aria-controls="tabs-main-images"
      role="tab"
      aria-selected="false"
    >
      图片
    </button>
    <button
      type="button"
      class="grow btn btn-text active-tab:bg-primary active-tab:text-white hover:text-primary hover:bg-primary/20"
      id="tabs-main-settings-item"
      data-tab="#tabs-main-settings"
      aria-controls="tabs-main-settings"
      role="tab"
      aria-selected="false"
    >
      设置
    </button>
  </nav>
</template>
