import type { ReactNode } from 'react';
import { LogOut, Monitor, Moon, Sun } from 'lucide-react';
import type { SaveFolder, Session } from '../lib/api';
import { cn } from '../lib/cn';
import { formatBytes, platformLabel } from '../lib/format';
import { useTheme, type ThemePreference } from '../lib/theme';
import { DeviceAvatar } from './DeviceAvatar';
import { FolderPicker, type FolderChoices } from './FolderPicker';
import { Button } from './ui/button';
import { Switch } from './ui/menu';
import { Modal } from './ui/modal';

interface Props {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  session: Session;
  onAutoAccept: (value: boolean) => void;
  notifications: { supported: boolean; enabled: boolean; toggle: () => void };
  /** Host: where accepted files go by default. */
  folders: FolderChoices | null;
  onDefaultFolder: (folder: SaveFolder) => void;
  pairedCount: number;
  onPair: () => void;
  onDevices: () => void;
  /** Phone only. */
  onRename: () => void;
  onForget: () => void;
  /** Phone on plain HTTP: set up HTTPS. */
  onSecureSetup?: () => void;
  /** Phone on HTTPS, not yet installed as an app. */
  onInstall?: () => void;
}

const THEMES: { value: ThemePreference; label: string; icon: typeof Sun }[] = [
  { value: 'system', label: 'System', icon: Monitor },
  { value: 'light', label: 'Light', icon: Sun },
  { value: 'dark', label: 'Dark', icon: Moon },
];

/** The few settings there are, in one place. */
export function SettingsDialog(props: Props) {
  const { session } = props;
  const me = session.device;
  const { preference, setPreference } = useTheme();

  return (
    <Modal
      open={props.open}
      onOpenChange={props.onOpenChange}
      title="Settings"
      className="sm:max-w-md"
      footer={
        !session.host && (
          <Button variant="danger-ghost" size="sm" onClick={props.onForget} className="sm:mr-auto">
            <LogOut /> Unpair this device
          </Button>
        )
      }
    >
      <div className="space-y-5 touch:space-y-6">
        <Group title="This device">
          <Row
            icon={<DeviceAvatar device={me} size="md" />}
            label={me.name}
            description={session.host ? `${platformLabel(me.platform)} · Runs AndroidDrop` : `${platformLabel(me.platform)} · Connected to ${session.serverName}`}
          >
            {!session.host && (
              <Button size="xs" onClick={props.onRename}>
                Rename
              </Button>
            )}
          </Row>
        </Group>

        <Group title="Receiving">
          <Row label="Accept automatically" description="Skip the Accept / Decline prompt for your paired devices.">
            <Toggle checked={session.autoAccept} onChange={props.onAutoAccept} label="Accept automatically" />
          </Row>
          {props.folders && (
            <Row
              label="Save files to"
              description={session.freeBytes !== null ? `You can still pick another folder when accepting. ${formatBytes(session.freeBytes)} free.` : 'You can still pick another folder when accepting.'}
            >
              <FolderPicker choices={props.folders} value={props.folders.folders[0]} onChange={props.onDefaultFolder} align="end" />
            </Row>
          )}
          {session.host && props.notifications.supported && (
            <Row label="Notifications" description="Alert me when something arrives while AndroidDrop is in the background.">
              <Toggle checked={props.notifications.enabled} onChange={props.notifications.toggle} label="Notifications" />
            </Row>
          )}
        </Group>

        <Group title="Appearance">
          <div role="radiogroup" aria-label="Theme" className="grid grid-cols-3 gap-1 rounded-xl bg-bg-muted p-1">
            {THEMES.map(({ value, label, icon: Icon }) => (
              <button
                key={value}
                type="button"
                role="radio"
                aria-checked={preference === value}
                onClick={() => setPreference(value)}
                className={cn(
                  'flex h-8 items-center justify-center gap-1.5 rounded-lg text-[13px] font-medium touch:h-11 touch:rounded-[10px] touch:text-[15px] touch:[&_svg]:size-[18px] outline-none focus-visible:ring-[3px] focus-visible:ring-accent/25',
                  preference === value ? 'bg-bg text-fg shadow-control' : 'text-fg-muted hover:text-fg',
                )}
              >
                <Icon className="size-3.5" /> {label}
              </button>
            ))}
          </div>
        </Group>

        {session.host ? (
          <Group title="Devices">
            <Row label={props.pairedCount === 1 ? '1 paired device' : `${props.pairedCount} paired devices`} description="Phones and browsers that can send to this computer.">
              <div className="flex gap-1.5">
                <Button size="xs" onClick={props.onDevices}>
                  Manage
                </Button>
                <Button variant="primary" size="xs" onClick={props.onPair}>
                  Pair
                </Button>
              </div>
            </Row>
          </Group>
        ) : (
          <Group title="Connection">
            <Row
              label={props.onSecureSetup ? 'Secure connection is off' : window.isSecureContext ? 'Secure connection is on' : 'Plain connection'}
              description={props.onSecureSetup ? 'Set it up once to share from any app and encrypt everything.' : window.isSecureContext ? 'Everything between this phone and your computer is encrypted.' : undefined}
            >
              {props.onSecureSetup && (
                <Button variant="primary" size="xs" onClick={props.onSecureSetup}>
                  Set up
                </Button>
              )}
              {props.onInstall && (
                <Button variant="primary" size="xs" onClick={props.onInstall}>
                  Install app
                </Button>
              )}
            </Row>
          </Group>
        )}
      </div>
    </Modal>
  );
}

function Group({ title, children }: { title: string; children: ReactNode }) {
  return (
    <section>
      <h3 className="mb-1.5 text-[12px] touch:text-[13.5px] font-semibold text-fg-subtle">{title}</h3>
      <div className="divide-y divide-border rounded-xl border border-border touch:rounded-2xl">{children}</div>
    </section>
  );
}

function Row({ icon, label, description, children }: { icon?: ReactNode; label: ReactNode; description?: ReactNode; children?: ReactNode }) {
  return (
    <div className="flex items-center gap-3 px-3.5 py-3 touch:px-4 touch:py-3.5">
      {icon}
      <div className="min-w-0 flex-1">
        <p className="truncate text-[13.5px] touch:text-[15.5px] font-medium">{label}</p>
        {description && <p className="mt-0.5 text-[12.5px] touch:text-[14px] leading-snug text-fg-muted">{description}</p>}
      </div>
      {children && <div className="shrink-0">{children}</div>}
    </div>
  );
}

function Toggle({ checked, onChange, label }: { checked: boolean; onChange: (value: boolean) => void; label: string }) {
  return (
    <button type="button" role="switch" aria-checked={checked} aria-label={label} onClick={() => onChange(!checked)} className="rounded-full outline-none focus-visible:ring-[3px] focus-visible:ring-accent/25">
      <Switch checked={checked} />
    </button>
  );
}
