<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '@/api/http'

interface SysRole {
  id?: number
  code: string
  name: string
  description?: string
  status?: string
  remark?: string
}

interface SysMenu {
  id?: number
  code: string
  name: string
  parentId?: number
  path?: string
  sortNo?: number
  status?: string
  children?: SysMenu[]
}

interface SysUser {
  id: number
  userCode: string
  name: string
}

const tab = ref('roles')
const loading = ref(false)
const roles = ref<SysRole[]>([])
const menuTree = ref<SysMenu[]>([])
const users = ref<SysUser[]>([])

// 角色
const roleDialog = ref(false)
const savingRole = ref(false)
const roleForm = reactive<SysRole>({ code: '', name: '', status: 'ACTIVE' })

// 菜单
const menuDialog = ref(false)
const savingMenu = ref(false)
const menuForm = reactive<SysMenu>({ code: '', name: '', parentId: 0, sortNo: 0, status: 'ACTIVE' })

// 授权
const grantDialog = ref(false)
const grantRole = ref<SysRole | null>(null)
const menuTreeRef = ref()
const checkedMenus = ref<string[]>([])

// 用户角色
const userId = ref<number | null>(null)
const userRoleCodes = ref<string[]>([])
const permissions = ref<{ roleCodes: string[]; menuCodes: string[] }>({ roleCodes: [], menuCodes: [] })

async function load() {
  loading.value = true
  try {
    const [roleRes, menuRes, userRes] = await Promise.all([
      http.get<SysRole[]>('/admin/rbac/roles'),
      http.get<SysMenu[]>('/admin/rbac/menus/tree'),
      http.get<SysUser[]>('/admin/users'),
    ])
    roles.value = roleRes.data ?? []
    menuTree.value = menuRes.data ?? []
    users.value = userRes.data ?? []
  } catch (e) {
    ElMessage.error('加载失败：' + (e as Error).message)
  } finally {
    loading.value = false
  }
}

// ---------- 角色 ----------
function openCreateRole() {
  Object.assign(roleForm, { id: undefined, code: '', name: '', description: '', status: 'ACTIVE', remark: '' })
  roleDialog.value = true
}
function openEditRole(row: SysRole) {
  Object.assign(roleForm, row)
  roleDialog.value = true
}
async function saveRole() {
  if (!roleForm.code || !roleForm.name) {
    ElMessage.warning('编码与名称必填')
    return
  }
  savingRole.value = true
  try {
    await http.post('/admin/rbac/roles', { ...roleForm })
    ElMessage.success('已保存')
    roleDialog.value = false
    await load()
  } finally {
    savingRole.value = false
  }
}
async function removeRole(row: SysRole) {
  await ElMessageBox.confirm(`删除角色 ${row.code}？`, '提示', { type: 'warning' })
  await http.delete(`/admin/rbac/roles/${row.id}`)
  await load()
}

// ---------- 授权 ----------
async function openGrant(row: SysRole) {
  grantRole.value = row
  const { data } = await http.get<string[]>(`/admin/rbac/roles/${row.code}/menus`)
  checkedMenus.value = data ?? []
  grantDialog.value = true
  // 等树渲染后再设置勾选
  setTimeout(() => {
    menuTreeRef.value?.setCheckedKeys(checkedMenus.value)
  }, 0)
}
async function saveGrant() {
  if (!grantRole.value) return
  const keys = [...(menuTreeRef.value?.getCheckedKeys() ?? []), ...(menuTreeRef.value?.getHalfCheckedKeys() ?? [])]
  await http.post(`/admin/rbac/roles/${grantRole.value.code}/menus`, keys)
  ElMessage.success('授权已保存')
  grantDialog.value = false
}

// ---------- 菜单 ----------
function openCreateMenu(parentId = 0) {
  Object.assign(menuForm, { id: undefined, code: '', name: '', parentId, path: '', sortNo: 0, status: 'ACTIVE', remark: '' })
  menuDialog.value = true
}
function openEditMenu(row: SysMenu) {
  Object.assign(menuForm, { ...row, children: undefined })
  menuDialog.value = true
}
async function saveMenu() {
  if (!menuForm.code || !menuForm.name) {
    ElMessage.warning('编码与名称必填')
    return
  }
  savingMenu.value = true
  try {
    await http.post('/admin/rbac/menus', { ...menuForm })
    ElMessage.success('已保存')
    menuDialog.value = false
    await load()
  } finally {
    savingMenu.value = false
  }
}
async function removeMenu(row: SysMenu) {
  await ElMessageBox.confirm(`删除菜单「${row.name}」及其子菜单？`, '提示', { type: 'warning' })
  await http.delete(`/admin/rbac/menus/${row.id}`)
  await load()
}

// ---------- 用户角色 ----------
async function loadUserRoles() {
  if (!userId.value) return
  const [rolesRes, permsRes] = await Promise.all([
    http.get<string[]>(`/admin/rbac/users/${userId.value}/roles`),
    http.get(`/admin/rbac/users/${userId.value}/permissions`),
  ])
  userRoleCodes.value = rolesRes.data ?? []
  permissions.value = { roleCodes: permsRes.data.roleCodes ?? [], menuCodes: permsRes.data.menuCodes ?? [] }
}
async function saveUserRoles() {
  if (!userId.value) return
  await http.post(`/admin/rbac/users/${userId.value}/roles`, userRoleCodes.value)
  ElMessage.success('用户角色已保存')
  await loadUserRoles()
}

onMounted(load)
</script>

<template>
  <div class="view">
    <el-card shadow="never">
      <template #header>
        <div class="head">
          <b>平台层 · 系统层 · 用户 / 角色 / 菜单</b>
          <span class="muted">角色维护 + 菜单树 + 角色授权菜单；用户角色沿用 sys_user.role_codes</span>
        </div>
      </template>

      <el-tabs v-model="tab">
        <el-tab-pane label="角色" name="roles">
          <div class="toolbar">
            <el-button type="primary" size="small" @click="openCreateRole">新建角色</el-button>
          </div>
          <el-table v-loading="loading" :data="roles" border stripe size="small">
            <el-table-column prop="id" label="ID" width="70" />
            <el-table-column prop="code" label="编码" width="160" />
            <el-table-column prop="name" label="名称" min-width="140" />
            <el-table-column prop="description" label="描述" min-width="220" show-overflow-tooltip />
            <el-table-column label="状态" width="90">
              <template #default="{ row }">
                <el-tag size="small" :type="row.status === 'ACTIVE' ? 'success' : 'info'">{{ row.status }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="200" fixed="right">
              <template #default="{ row }">
                <el-button link type="success" size="small" @click="openGrant(row)">授权菜单</el-button>
                <el-button link type="primary" size="small" @click="openEditRole(row)">编辑</el-button>
                <el-button link type="danger" size="small" @click="removeRole(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="菜单" name="menus">
          <div class="toolbar">
            <el-button type="primary" size="small" @click="openCreateMenu(0)">新建根菜单</el-button>
          </div>
          <el-table v-loading="loading" :data="menuTree" row-key="id" :tree-props="{ children: 'children' }" border stripe size="small">
            <el-table-column prop="name" label="名称" min-width="180" />
            <el-table-column prop="code" label="编码" width="180" />
            <el-table-column prop="path" label="路由" min-width="200" />
            <el-table-column prop="sortNo" label="排序" width="80" />
            <el-table-column label="操作" width="220" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" size="small" @click="openCreateMenu(row.id)">加子菜单</el-button>
                <el-button link type="primary" size="small" @click="openEditMenu(row)">编辑</el-button>
                <el-button link type="danger" size="small" @click="removeMenu(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="用户角色" name="user-roles">
          <div class="toolbar">
            <el-select v-model="userId" filterable placeholder="选择用户" style="width: 260px" @change="loadUserRoles">
              <el-option v-for="u in users" :key="u.id" :label="`${u.name} (${u.userCode})`" :value="u.id" />
            </el-select>
            <el-button type="primary" size="small" :disabled="!userId" @click="saveUserRoles">保存</el-button>
          </div>
          <el-checkbox-group v-model="userRoleCodes">
            <el-checkbox v-for="r in roles" :key="r.code" :value="r.code">{{ r.name }} ({{ r.code }})</el-checkbox>
          </el-checkbox-group>
          <div v-if="userId" class="perms">
            <p><b>权限汇总：</b>角色 {{ permissions.roleCodes.join('、') || '无' }}</p>
            <p><b>菜单：</b>{{ permissions.menuCodes.join('、') || '无' }}</p>
          </div>
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <el-dialog v-model="roleDialog" :title="roleForm.id ? '编辑角色' : '新建角色'" width="520px">
      <el-form label-width="90px">
        <el-form-item label="编码" required><el-input v-model="roleForm.code" :disabled="!!roleForm.id" /></el-form-item>
        <el-form-item label="名称" required><el-input v-model="roleForm.name" /></el-form-item>
        <el-form-item label="描述"><el-input v-model="roleForm.description" /></el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="roleForm.status">
            <el-radio value="ACTIVE">启用</el-radio>
            <el-radio value="DISABLED">停用</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="roleDialog = false">取消</el-button>
        <el-button type="primary" :loading="savingRole" @click="saveRole">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="menuDialog" :title="menuForm.id ? '编辑菜单' : '新建菜单'" width="560px">
      <el-form label-width="90px">
        <el-form-item label="编码" required><el-input v-model="menuForm.code" :disabled="!!menuForm.id" /></el-form-item>
        <el-form-item label="名称" required><el-input v-model="menuForm.name" /></el-form-item>
        <el-form-item label="父菜单">
          <el-tree-select
            v-model="menuForm.parentId"
            :data="[{ id: 0, name: '（根）', children: menuTree }, ...menuTree]"
            :props="{ label: 'name', value: 'id', children: 'children' }"
            check-strictly
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="路由"><el-input v-model="menuForm.path" placeholder="/admin/xxx" /></el-form-item>
        <el-form-item label="排序"><el-input-number v-model="menuForm.sortNo" :min="0" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="menuDialog = false">取消</el-button>
        <el-button type="primary" :loading="savingMenu" @click="saveMenu">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="grantDialog" :title="`授权菜单 · ${grantRole?.name ?? ''}`" width="520px">
      <el-tree
        ref="menuTreeRef"
        :data="menuTree"
        node-key="code"
        show-checkbox
        default-expand-all
        :props="{ label: 'name', children: 'children' }"
      />
      <template #footer>
        <el-button @click="grantDialog = false">取消</el-button>
        <el-button type="primary" @click="saveGrant">保存授权</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.view { display: flex; flex-direction: column; gap: 12px; }
.head { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.muted { color: #909399; font-size: 12px; }
.toolbar { display: flex; align-items: center; gap: 10px; margin-bottom: 10px; }
.perms { margin-top: 12px; color: #606266; font-size: 13px; }
</style>
